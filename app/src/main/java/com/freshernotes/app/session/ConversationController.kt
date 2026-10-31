package com.freshernotes.app.session

import com.freshernotes.app.audio.SpeechInput
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.settings.SettingsRepository
import com.freshernotes.app.device.DeviceMonitor
import com.freshernotes.app.device.PerfStats
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.engine.LlamaLanguageModel
import com.freshernotes.app.engine.MissingModelException
import com.freshernotes.app.engine.SynthesizerProvider
import com.freshernotes.core.PerformancePolicy
import com.freshernotes.core.Speaker
import com.freshernotes.core.llm.ContextManager
import com.freshernotes.core.llm.ConversationMemory
import com.freshernotes.core.llm.Persona
import com.freshernotes.core.llm.QuestionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
) {
    private val _state = MutableStateFlow<FresherState>(FresherState.Idle)
    val state: StateFlow<FresherState> = _state.asStateFlow()
    private val mutex = Mutex()

    /** Called with every transcribed or typed senior utterance. Returns true if the senior asked to wrap up. */
    suspend fun onSeniorTurn(sessionId: Long, text: String, speech: SpeechInput?): Boolean {
        sessions.addTurn(sessionId, Speaker.SENIOR, text)
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
                    askQuestion(sessionId, prefs.maxReplyWords, prefs.voice == "piper", prefs.speechRate, speech)
                    false
                }
            }
        }
    }

    private suspend fun askQuestion(sessionId: Long, maxWords: Int, piper: Boolean, rate: Float, speech: SpeechInput?) {
        try {
            val health = device.state.value
            if (PerformancePolicy.shouldPauseLlm(health.thermalStatus)) {
                _state.value = FresherState.Error("Phone is too hot; the fresher will ask again once it cools. Keep talking: everything is saved.")
                return
            }
            val engine = engines.llm()
            engine.setThreads(health.budget.llmThreads)
            val model = LlamaLanguageModel(engine)
            val context = ContextManager(model)
            val system = Persona.systemPrompt(maxWords)
            val session = requireNotNull(sessions.getSession(sessionId))
            var memory = ConversationMemory(session.runningSummary, session.summarizedTurnCount, sessions.turns(sessionId))
            if (context.needsCompaction(memory, system)) {
                _state.value = FresherState.Summarizing
                memory = context.compact(memory)
                sessions.updateSummary(sessionId, memory.summary, memory.summarizedCount)
            }
            _state.value = FresherState.Thinking("")
            val raw = StringBuilder()
            val t0 = System.nanoTime()
            var firstTokenMs = -1L
            var chunks = 0
            // ~1.6 tokens per word, plus slack so the cleaner can cut at the first question mark.
            model.stream(context.messages(memory, system), maxTokens = (maxWords * 2).coerceAtLeast(48), temperature = 0.7f)
                .collect {
                    if (firstTokenMs < 0) firstTokenMs = (System.nanoTime() - t0) / 1_000_000
                    chunks++
                    raw.append(it)
                    _state.value = FresherState.Thinking(raw.toString())
                }
            if (firstTokenMs >= 0) perf.recordLlm(firstTokenMs, chunks, (System.nanoTime() - t0) / 1_000_000)
            val question = Persona.cleanReply(raw.toString(), maxWords)
            if (question.isBlank()) {
                _state.value = FresherState.Idle
                return
            }
            sessions.addTurn(sessionId, Speaker.FRESHER, question)
            _state.value = FresherState.Speaking(question)
            speech?.muted = true
            try {
                voices.synthesizer(preferPiper = piper).speak(question, rate)
            } finally {
                speech?.muted = false
            }
            _state.value = FresherState.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingModelException) {
            _state.value = FresherState.Error("${e.kind.label} is not installed. Add it in Settings.")
        } catch (e: Exception) {
            _state.value = FresherState.Error(e.message ?: "The fresher could not respond")
        }
    }

    fun clearError() {
        if (_state.value is FresherState.Error) _state.value = FresherState.Idle
    }
}
