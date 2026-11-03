package com.maheshreddy.mym.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.audio.ListenState
import com.maheshreddy.mym.audio.SpeechInput
import com.maheshreddy.mym.data.SessionRepository
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.device.DeviceMonitor
import com.maheshreddy.mym.di.ApplicationScope
import com.maheshreddy.mym.engine.EngineStatus
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.library.SessionFiler
import com.maheshreddy.mym.session.ConversationController
import com.maheshreddy.mym.session.FresherState
import com.maheshreddy.mym.core.llm.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long one utterance was and how long whisper took to transcribe it. */
data class TurnTiming(val audioSeconds: Double, val processingSeconds: Double)

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val speech: SpeechInput,
    private val conversation: ConversationController,
    private val filer: SessionFiler,
    private val library: LibraryRepository,
    private val device: DeviceMonitor,
    private val engines: Engines,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val sessionId: Long = checkNotNull(savedState["sessionId"])

    val session = sessions.observeSession(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val turns = sessions.observeTurns(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Entries this session produced (after wrap-up). */
    val entries = library.observeSessionEntries(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val listenState: StateFlow<ListenState> = speech.state
    val level: StateFlow<Float> = speech.level
    val fresherState: StateFlow<FresherState> = conversation.state
    val engineStatus: StateFlow<EngineStatus> = engines.status

    /** Thermal / battery warning to show, if any. */
    val deviceWarning = device.state.map { it.budget.warning }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _timings = MutableStateFlow<Map<Long, TurnTiming>>(emptyMap())
    /** Transcription timing per senior turn id, for turns spoken while this screen is open. */
    val timings: StateFlow<Map<Long, TurnTiming>> = _timings.asStateFlow()

    private val _listenOnly = MutableStateFlow(false)
    /** Listen mode (v2 §4.1): the butler stays quiet and just captures. Interview mode asks one follow-up at a time. */
    val listenOnly: StateFlow<Boolean> = _listenOnly.asStateFlow()

    fun setListenOnly(on: Boolean) { _listenOnly.value = on }

    private val _resumePrompt = MutableStateFlow(false)
    /** Reopening an unfinished session that has turns and has been idle: offer Resume or Wrap up. */
    val resumePrompt: StateFlow<Boolean> = _resumePrompt.asStateFlow()
    fun resume() { _resumePrompt.value = false }

    private val _closed = MutableStateFlow(false)
    /** True once an empty session was wrapped up and therefore deleted: the screen goes back. */
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    /** "Please respond" signals; CONFLATED so a backlog collapses into one reply using the latest turns. */
    private val respondRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        device.start()
        // Transcripts are saved the moment they arrive, never queued behind the LLM or text-to-speech.
        viewModelScope.launch {
            speech.transcripts.collect { t ->
                val id = onSeniorSpoke(t.text)
                _timings.update { it + (id to TurnTiming(t.audioSeconds, t.processingSeconds)) }
            }
        }
        // The fresher replies on its own coroutine.
        viewModelScope.launch {
            for (ignored in respondRequests) conversation.respond(sessionId, speech)
        }
        // Load whisper, then the LLM, in the background so the first turn does not wait for them.
        viewModelScope.launch {
            val s = sessions.getSession(sessionId)
            if (s?.endedAt == null) engines.preload()
            if (s != null && s.endedAt == null && sessions.turnCount(sessionId) > 0 &&
                System.currentTimeMillis() - s.lastActivityAt > RESUME_PROMPT_AFTER_MS
            ) _resumePrompt.value = true
        }
    }

    /** Mic button: toggles recording. Push-to-talk transcribes on stop; continuous mode segments with VAD. */
    fun toggleMic() {
        if (speech.isRecording()) {
            speech.stop()
        } else {
            viewModelScope.launch {
                val allowed = device.state.value.budget.allowContinuousListening
                speech.start(viewModelScope, continuous = settings.settings.first().continuousMode && allowed)
            }
        }
    }

    /** Typed input path; also useful when the microphone is unavailable. */
    fun submitText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { onSeniorSpoke(trimmed) }
    }

    private suspend fun onSeniorSpoke(text: String): Long {
        val id = conversation.recordSeniorTurn(sessionId, text)
        if (Persona.isWrapUp(text)) finish() else if (!_listenOnly.value) respondRequests.trySend(Unit)
        return id
    }

    fun wrapUp() {
        viewModelScope.launch { finish() }
    }

    fun dismissError() = conversation.clearError()

    private suspend fun finish() {
        speech.stop()
        _resumePrompt.value = false
        // Nothing was said: there is nothing to file, so remove the session instead of leaving an empty one.
        if (sessions.turnCount(sessionId) == 0) {
            sessions.deleteIfEmpty(sessionId)
            _closed.value = true
            return
        }
        sessions.endSession(sessionId)
        // Runs in the application scope so it completes even if the user leaves the screen.
        appScope.launch { filer.file(sessionId) }
    }

    /** Retry filing after a failure. */
    fun retryFiling() {
        appScope.launch { filer.file(sessionId) }
    }

    /** v2 §6.4: delete a test or junk session completely (turns, entries, search). */
    fun deleteSession(onDone: () -> Unit) {
        speech.stop()
        appScope.launch {
            library.deleteSessionCompletely(sessionId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
    }

    override fun onCleared() {
        speech.stop()
        // Opened but never used: do not leave an empty "In progress" session behind.
        appScope.launch { sessions.deleteIfEmpty(sessionId) }
    }

    companion object {
        const val RESUME_PROMPT_AFTER_MS = 10 * 60 * 1000L
    }
}
