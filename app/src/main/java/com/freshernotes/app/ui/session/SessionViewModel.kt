package com.freshernotes.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.core.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sessions: SessionRepository,
) : ViewModel() {
    val sessionId: Long = checkNotNull(savedState["sessionId"])

    val session = sessions.observeSession(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val turns = sessions.observeTurns(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Typed input path; also useful when the microphone is unavailable. */
    fun submitText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { sessions.addTurn(sessionId, Speaker.SENIOR, trimmed) }
    }

    fun wrapUp() {
        viewModelScope.launch { sessions.endSession(sessionId) }
    }
}
