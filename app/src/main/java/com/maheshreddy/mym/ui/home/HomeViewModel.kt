package com.maheshreddy.mym.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.data.ButlerRepository
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.data.SessionRepository
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.models.ModelStore
import com.maheshreddy.mym.ui.library.LibraryUi
import com.maheshreddy.mym.core.library.NudgeEngine
import com.maheshreddy.mym.core.library.NudgeEntry
import com.maheshreddy.mym.core.library.NudgeInput
import com.maheshreddy.mym.core.library.NudgeTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessions: SessionRepository,
    private val library: LibraryRepository,
    models: ModelStore,
    private val butler: ButlerRepository,
    nudges: com.maheshreddy.mym.nudges.NudgeService,
) : ViewModel() {
    private val tiles = combine(library.observeAllTopics(), library.observeSubjects(), library.observeTopicCounts()) { topics, subjects, counts ->
        Triple(topics, subjects, LibraryUi.tiles(topics, subjects, counts))
    }
    private val extras = combine(nudges.current, butler.profile) { a, b -> a to b }

    val state = combine(
        tiles,
        library.observeInboxCount(),
        sessions.recentSessions(5),
        models.installed,
        extras,
    ) { (topics, subjects, tiles), inbox, recent, installed, (nudge, profile) ->
        HomeUiState(
            greeting = greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) + profile.callsYou.trim().takeIf { it.isNotEmpty() }?.let { ", $it" }.orEmpty(),
            missingModels = ModelKind.entries.filter { it.required && it !in installed }.map { it.label },
            nudge = nudge,
            inboxCount = inbox,
            // Topics are ordered by last update; continue the latest one that has entries.
            continueTopic = tiles.firstOrNull { it.entries > 0 },
            shelves = subjects.map { s -> ShelfTile(s.id, s.title, topics.count { it.subjectId == s.id }) },
            recent = recent.map { s ->
                SessionRow(
                    s.id, s.topicHint?.takeIf { it.isNotBlank() } ?: s.title,
                    when {
                        s.endedAt == null -> "In progress"
                        s.notesStatus == "PENDING" -> "Filing…"
                        s.notesStatus == "FAILED" -> "Filing failed, retried on next launch"
                        else -> "Ended"
                    },
                    summary = s.summary,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** "Later" on the nudge card: hide it until tomorrow. */
    fun snoozeNudge() = viewModelScope.launch {
        butler.snoozeNudge(NudgeEngine.snoozeUntil(LibraryRepository.startOfDay(System.currentTimeMillis())))
    }

    companion object {
        fun greeting(hour: Int) = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
