package com.freshernotes.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.audio.ListenState
import com.freshernotes.app.audio.SpeechInput
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.settings.SettingsRepository
import com.freshernotes.core.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val speech: SpeechInput,
) : ViewModel() {
    val sessionId: Long = checkNotNull(savedState["sessionId"])

    val session = sessions.observeSession(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val turns = sessions.observeTurns(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val listenState: StateFlow<ListenState> = speech.state

    init {
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
                speech.start(viewModelScope, continuous = settings.settings.first().continuousMode)
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
        sessions.addTurn(sessionId, Speaker.SENIOR, text)
    }

    fun wrapUp() {
        speech.stop()
        viewModelScope.launch { sessions.endSession(sessionId) }
    }

    override fun onCleared() {
        speech.stop()
    }
}
