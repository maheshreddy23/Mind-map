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
    settings: com.maheshreddy.mym.data.settings.SettingsRepository,
) : ViewModel() {
    /** Null until settings are read; false shows the first-run onboarding. */
    val onboarded = settings.settings.map<com.maheshreddy.mym.data.settings.AppSettings, Boolean?> { it.onboarded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val butlerName = butler.profile.map { it.displayName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.maheshreddy.mym.core.llm.ButlerProfile.DEFAULT_NAME)

    /** Starts a session; [topic] titles it "Topic: …" when continuing a topic. */
    private val starting = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Starts (or reuses a still-empty) session and opens it. Guarded so a double tap opens one session, not two:
     * the cause of two sessions created in the same minute.
     */
    fun start(onStarted: (Long) -> Unit, topic: String? = null) {
        if (!starting.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val time = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())
                onStarted(sessions.startOrReuse(if (topic != null) "$topic · $time" else "Session $time", topic))
            } finally {
                // Keep the guard briefly so a second tap queued behind navigation is ignored too.
                kotlinx.coroutines.delay(600)
                starting.set(false)
            }
        }
    }
}
