package com.maheshreddy.mym.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
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
    butler: com.maheshreddy.mym.data.ButlerRepository,
) : ViewModel() {
    val butlerName = butler.profile.map { it.displayName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.maheshreddy.mym.core.llm.ButlerProfile.DEFAULT_NAME)
    val inboxCount = library.observeInboxCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Starts a session; [topic] titles it "Topic: …" when continuing a topic. */
    fun start(onStarted: (Long) -> Unit, topic: String? = null) {
        viewModelScope.launch {
            val time = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())
            onStarted(sessions.startSession(if (topic != null) "$topic · $time" else "Session $time"))
        }
    }
}
