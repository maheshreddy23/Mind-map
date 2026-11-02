package com.freshernotes.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshernotes.app.data.ButlerRepository
import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.models.ModelKind
import com.freshernotes.app.models.ModelStore
import com.freshernotes.app.ui.library.LibraryUi
import com.freshernotes.core.library.NudgeEngine
import com.freshernotes.core.library.NudgeEntry
import com.freshernotes.core.library.NudgeInput
import com.freshernotes.core.library.NudgeTopic
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
) : ViewModel() {
    private val tiles = combine(library.observeAllTopics(), library.observeSubjects(), library.observeTopicCounts()) { topics, subjects, counts ->
        Triple(topics, subjects, LibraryUi.tiles(topics, subjects, counts))
    }
    private val extras = combine(library.observeOldestFiled(), butler.profile, butler.nudgeSnoozedUntil) { a, b, c -> Triple(a, b, c) }

    val state = combine(
        tiles,
        library.observeInboxCount(),
        sessions.recentSessions(5),
        models.installed,
        extras,
    ) { (topics, subjects, tiles), inbox, recent, installed, (oldest, profile, snoozed) ->
        val now = System.currentTimeMillis()
        val startOfToday = LibraryRepository.startOfDay(now)
        val nudge = NudgeEngine.pick(
            NudgeInput(
                now = now, startOfToday = startOfToday, snoozedUntil = snoozed,
                topics = tiles.map { NudgeTopic(it.id, it.title, it.openQuestions) },
                oldest = oldest?.let { e -> NudgeEntry(e.id, e.title, topics.firstOrNull { it.id == e.topicId }?.title, e.createdAt) },
                sessionsToday = recent.count { it.startedAt >= startOfToday },
            ),
        )
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
