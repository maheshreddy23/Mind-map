package com.freshernotes.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.models.ModelKind
import com.freshernotes.app.models.ModelStore
import com.freshernotes.app.ui.library.LibraryUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessions: SessionRepository,
    library: LibraryRepository,
    models: ModelStore,
) : ViewModel() {
    private val tiles = combine(library.observeAllTopics(), library.observeSubjects(), library.observeTopicCounts()) { topics, subjects, counts ->
        Triple(topics, subjects, LibraryUi.tiles(topics, subjects, counts))
    }

    val state = combine(
        tiles,
        library.observeInboxCount(),
        sessions.recentSessions(5),
        models.installed,
    ) { (topics, subjects, tiles), inbox, recent, installed ->
        HomeUiState(
            greeting = greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)),
            missingModels = ModelKind.entries.filter { it.required && it !in installed }.map { it.label },
            inboxCount = inbox,
            // Topics are ordered by last update; continue the latest one that has entries.
            continueTopic = tiles.firstOrNull { it.entries > 0 },
            shelves = subjects.map { s -> ShelfTile(s.id, s.title, topics.count { it.subjectId == s.id }) },
            recent = recent.map { s ->
                SessionRow(
                    s.id, s.title,
                    when {
                        s.endedAt == null -> "In progress"
                        s.notesStatus == "PENDING" -> "Filing…"
                        s.notesStatus == "FAILED" -> "Filing failed, retried on next launch"
                        else -> "Ended"
                    },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        fun greeting(hour: Int) = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
