package com.freshernotes.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.audio.ListenState
import com.freshernotes.app.audio.SpeechInput
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.settings.SettingsRepository
import com.freshernotes.app.device.DeviceMonitor
import com.freshernotes.app.di.ApplicationScope
import com.freshernotes.app.notes.NoteGenerator
import com.freshernotes.app.session.ConversationController
import kotlinx.coroutines.CoroutineScope
import com.freshernotes.app.session.FresherState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val speech: SpeechInput,
    private val conversation: ConversationController,
    private val notes: NoteGenerator,
    private val device: DeviceMonitor,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val sessionId: Long = checkNotNull(savedState["sessionId"])

    val session = sessions.observeSession(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val turns = sessions.observeTurns(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val listenState: StateFlow<ListenState> = speech.state
    val fresherState: StateFlow<FresherState> = conversation.state
    /** Thermal / battery warning to show, if any. */
    val deviceWarning = device.state.map { it.budget.warning }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        device.start()
        viewModelScope.launch {
            speech.transcripts.collect { onSeniorSpoke(it) }
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

    private suspend fun onSeniorSpoke(text: String) {
        val wrapUp = conversation.onSeniorTurn(sessionId, text, speech)
        if (wrapUp) finish()
    }

    fun wrapUp() {
        viewModelScope.launch { finish() }
    }

    fun dismissError() = conversation.clearError()

    private suspend fun finish() {
        speech.stop()
        sessions.endSession(sessionId)
        // Runs in the application scope so it completes even if the user leaves the screen.
        appScope.launch { notes.generate(sessionId) }
    }

    override fun onCleared() {
        speech.stop()
    }
}
