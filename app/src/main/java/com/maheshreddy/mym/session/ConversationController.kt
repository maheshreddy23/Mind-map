package com.maheshreddy.mym.session

import com.maheshreddy.mym.audio.SpeechInput
import com.maheshreddy.mym.data.SessionRepository
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.device.DeviceMonitor
import com.maheshreddy.mym.device.PerfStats
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.engine.LlamaLanguageModel
import com.maheshreddy.mym.engine.MissingModelException
import com.maheshreddy.mym.engine.SynthesizerProvider
import com.maheshreddy.mym.core.OfficialChecksums
import com.maheshreddy.mym.core.PerformancePolicy
import com.maheshreddy.mym.core.Speaker
import com.maheshreddy.mym.core.llm.ContextManager
import com.maheshreddy.mym.core.llm.ConversationMemory
import com.maheshreddy.mym.core.llm.Persona
import com.maheshreddy.mym.core.llm.QuestionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface FresherState {
    data object Idle : FresherState
    data object Summarizing : FresherState
    data class Thinking(val partial: String) : FresherState
    data class Speaking(val text: String) : FresherState
    data object WrappedUp : FresherState
    data class Error(val message: String) : FresherState
}

/**
 * The conversation loop for one session: every senior turn is saved immediately; then, depending on the
 * question cadence, the fresher's next question is generated with a rolling context window (older turns are
 * summarized once the prompt passes 70% of the context), saved, and spoken while the microphone is muted.
 */
class ConversationController @Inject constructor(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val engines: Engines,
    private val voices: SynthesizerProvider,
    private val device: DeviceMonitor,
    private val perf: PerfStats,
    private val butler: com.maheshreddy.mym.data.ButlerRepository,
) {
    private val _state = MutableStateFlow<FresherState>(FresherState.Idle)
    val state: StateFlow<FresherState> = _state.asStateFlow()
    private val mutex = Mutex()

    /**
     * Saves a senior turn immediately, independent of any reply in progress, so transcripts never queue behind
     * the LLM or text-to-speech. Returns the new turn's id.
     */
    suspend fun recordSeniorTurn(sessionId: Long, text: String): Long {
        val id = sessions.addTurn(sessionId, Speaker.SENIOR, text)
        if (Persona.isWrapUp(text)) _state.value = FresherState.WrappedUp
        return id
    }

    /** Record + respond in one call (typed input and tests). Returns true if the senior asked to wrap up. */
    suspend fun onSeniorTurn(sessionId: Long, text: String, speech: SpeechInput?): Boolean {
        recordSeniorTurn(sessionId, text)
        return respond(sessionId, speech)
    }

    /**
     * Decides whether to ask and, if so, asks, using the turns saved so far (including any spoken while a previous
     * reply was being generated). Returns true if the latest senior turn asked to wrap up.
     */
    suspend fun respond(sessionId: Long, speech: SpeechInput?): Boolean {
        return mutex.withLock {
            val prefs = settings.settings.first()
            val turns = sessions.turns(sessionId)
            when (QuestionPolicy.decide(turns, prefs.questionEveryNTurns)) {
                QuestionPolicy.Decision.WrapUp -> {
                    _state.value = FresherState.WrappedUp
                    true
                }
                QuestionPolicy.Decision.Listen -> false
                QuestionPolicy.Decision.Ask -> {
                    askQuestion(sessionId, prefs.maxReplyWords, prefs.voice == "piper", prefs.speechRate, speech, prefs.pitch, prefs.systemVoice)
                    false
                }
            }
        }
    }

    private suspend fun askQuestion(
        sessionId: Long,
        maxWords: Int,
        piper: Boolean,
        rate: Float,
        speech: SpeechInput?,
        pitch: Float = 1f,
        systemVoice: String = "",
    ) {
        try {
            val health = device.state.value
            if (PerformancePolicy.shouldPauseLlm(health.thermalStatus)) {
                _state.value = FresherState.Error("Phone is too hot; questions resume once it cools. Keep talking: everything is saved.")
                return
            }
            val engine = engines.llm()
            engine.setThreads(health.budget.llmThreads)
            val model = LlamaLanguageModel(engine)
            val context = ContextManager(model)
            // The butler's persona (name, tone, interview style). Constant within a session: a cached prompt prefix.
            val profile = butler.profile.first()
            val system = com.maheshreddy.mym.core.llm.ButlerPrompt.system(profile, maxWords)
            val session = requireNotNull(sessions.getSession(sessionId))
            var memory = ConversationMemory(session.runningSummary, session.summarizedTurnCount, sessions.turns(sessionId))
            if (context.needsCompaction(memory, system)) {
                _state.value = FresherState.Summarizing
                memory = context.compact(memory)
                sessions.updateSummary(sessionId, memory.summary, memory.summarizedCount)
            }
            // Attempt 1: normal sampling with a gentle reminder. Attempt 2: near-greedy with a strict reminder.
            // If the model still will not ask (small models sometimes summarize), use a generic follow-up.
            val nudge = com.maheshreddy.mym.core.llm.ButlerPrompt.nudge(profile, maxWords)
            val strict = com.maheshreddy.mym.core.llm.ButlerPrompt.nudge(profile, maxWords, strict = true)
            var question = generate(model, context.messages(memory, system, nudge), maxWords, 0.7f, record = true, name = profile.displayName)
            if (!Persona.isQuestion(question)) {
                question = generate(model, context.messages(memory, system, strict), maxWords, 0.2f, record = false, name = profile.displayName)
            }
            if (!Persona.isQuestion(question)) question = Persona.fallbackQuestion(memory.allTurns.size)
            sessions.addTurn(sessionId, Speaker.FRESHER, question)
            _state.value = FresherState.Speaking(question)
            speech?.muted = true
            try {
                // Never let a TTS engine that fails to report completion block the session.
                val budgetMs = 5_000L + (question.split(' ').size * 700f / rate.coerceAtLeast(0.5f)).toLong()
                val synth = voices.synthesizer(preferPiper = piper).also { it.configure(pitch, systemVoice) }
                if (withTimeoutOrNull(budgetMs) { synth.speak(question, rate) } == null) synth.stop()
            } finally {
                speech?.muted = false
            }
            _state.value = FresherState.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingModelException) {
            _state.value = FresherState.Error("${e.kind.label} is not installed. Add it in Settings.")
        } catch (e: Exception) {
            val msg = e.message ?: "No reply this time"
            _state.value = FresherState.Error(OfficialChecksums.loadFailureHint(msg)?.let { "$msg\n$it" } ?: msg)
        }
    }

    private suspend fun generate(
        model: LlamaLanguageModel,
        messages: List<com.maheshreddy.mym.core.llm.ChatMessage>,
        maxWords: Int,
        temperature: Float,
        record: Boolean,
        name: String,
    ): String {
        _state.value = FresherState.Thinking("")
        val raw = StringBuilder()
        val t0 = System.nanoTime()
        var firstTokenMs = -1L
        var chunks = 0
        // ~1.6 tokens per word, plus slack so the cleaner can cut at the first question mark.
        model.stream(messages, maxTokens = (maxWords * 2).coerceAtLeast(48), temperature = temperature).collect {
            if (firstTokenMs < 0) firstTokenMs = (System.nanoTime() - t0) / 1_000_000
            chunks++
            raw.append(it)
            _state.value = FresherState.Thinking(raw.toString())
        }
        if (record && firstTokenMs >= 0) perf.recordLlm(firstTokenMs, chunks, (System.nanoTime() - t0) / 1_000_000)
        return Persona.cleanReply(raw.toString(), maxWords, name)
    }

    fun clearError() {
        if (_state.value is FresherState.Error) _state.value = FresherState.Idle
    }
}
