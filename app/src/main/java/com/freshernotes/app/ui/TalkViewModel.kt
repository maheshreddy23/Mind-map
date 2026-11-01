package com.freshernotes.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** App-wide: the centre Talk button (one tap from every top-level screen) and the Inbox badge. */
@HiltViewModel
class TalkViewModel @Inject constructor(
    private val sessions: SessionRepository,
    library: LibraryRepository,
) : ViewModel() {
    val inboxCount = library.observeInboxCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Starts a session; [topic] titles it "Topic: …" when continuing a topic. */
    fun start(onStarted: (Long) -> Unit, topic: String? = null) {
        viewModelScope.launch {
            val time = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())
            onStarted(sessions.startSession(if (topic != null) "$topic · $time" else "Session $time"))
        }
    }
}
