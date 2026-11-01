package com.freshernotes.app.library

import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.db.NotesStatus
import com.freshernotes.app.data.settings.SettingsRepository
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.engine.LlamaLanguageModel
import com.freshernotes.app.search.DocKind
import com.freshernotes.app.search.SearchIndex
import com.freshernotes.core.ChatTurn
import com.freshernotes.core.Speaker
import com.freshernotes.core.library.EntryStatus
import com.freshernotes.core.library.EntryType
import com.freshernotes.core.library.FilingDecision
import com.freshernotes.core.library.FilingParser
import com.freshernotes.core.library.FilingPolicy
import com.freshernotes.core.library.FilingPrompts
import com.freshernotes.core.library.SubjectRef
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.ContextManager
import com.freshernotes.core.llm.LanguageModel
import com.freshernotes.core.llm.complete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Result of filing one session: how many entries were filed automatically and how many await confirmation. */
data class FilingSummary(val filed: Int, val inbox: Int)

/**
 * Wrap-up (v2 §6.3): LLM splits the session into entries → [FilingPolicy] decides auto-file vs Inbox →
 * [LibraryRepository] stores them and rebuilds the pages. The session is marked PENDING first, so filing resumes
 * on next launch if the app is killed midway. If the model's output cannot be parsed, the session still becomes
 * one Inbox entry, so nothing is lost.
 */
@Singleton
class SessionFiler @Inject constructor(
    private val sessions: SessionRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val engines: Engines,
    private val index: SearchIndex,
) {
    private val mutex = Mutex()

    suspend fun filePending() {
        for (s in sessions.pendingNotes()) file(s.id)
    }

    suspend fun file(sessionId: Long, model: LanguageModel? = null): Result<FilingSummary> = mutex.withLock {
        try {
            val session = requireNotNull(sessions.getSession(sessionId)) { "session $sessionId not found" }
            val turns = sessions.turns(sessionId)
            if (turns.none { it.speaker == Speaker.SENIOR }) {
                sessions.setNotes(sessionId, NotesStatus.DONE)
                return Result.success(FilingSummary(0, 0))
            }
            // A previous attempt stored its entries but was killed before marking the session DONE: do not file twice.
            if (library.observeSessionEntries(sessionId).first().isNotEmpty()) {
                sessions.setNotes(sessionId, NotesStatus.DONE)
                return Result.success(FilingSummary(0, 0))
            }
            val llm = model ?: LlamaLanguageModel(engines.llm())
            val snapshot = library.snapshot()
            val raw = llm.complete(promptFor(llm, snapshot, session.runningSummary, session.summarizedTurnCount, turns), maxTokens = MAX_TOKENS, temperature = 0.2f)
            val privacy = settings.settings.first().defaultPrivacy
            val decisions = FilingParser.parse(raw).map { FilingPolicy.decide(it, snapshot, privacy) }
                .ifEmpty { listOf(fallback(session.title, raw, turns, privacy)) }

            library.file(sessionId, decisions)
            sessions.setNotes(sessionId, NotesStatus.DONE)
            index.upsert(DocKind.SESSION, sessionId, session.title, ContextManager.transcript(turns))
            Result.success(FilingSummary(decisions.count { it.status == EntryStatus.FILED }, decisions.count { it.status == EntryStatus.INBOX }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sessions.setNotes(sessionId, NotesStatus.FAILED)
            Result.failure(e)
        }
    }

    /** One Inbox entry holding the model's text (or, failing that, what the user said). Never a catch-all topic. */
    private fun fallback(title: String, raw: String, turns: List<ChatTurn>, privacy: com.freshernotes.core.library.Privacy): FilingDecision {
        val said = turns.filter { it.speaker == Speaker.SENIOR }.joinToString("\n") { "- " + it.text.trim() }
        return FilingDecision(
            status = EntryStatus.INBOX, subjectId = null, topicId = null, proposedSubject = null, proposedTopic = null,
            type = EntryType.KNOWLEDGE, privacy = privacy, title = title,
            body = raw.trim().takeIf { it.length > 40 } ?: said,
            confidence = 0.1, reason = "The butler could not split this session; please file it",
        )
    }

    /** Uses the full transcript when it fits; otherwise the running summary plus the turns after it. */
    private fun promptFor(llm: LanguageModel, lib: List<SubjectRef>, summary: String, summarizedCount: Int, turns: List<ChatTurn>) =
        FilingPrompts.forSession(lib, "", turns).takeIf { fits(llm, it) }
            ?: FilingPrompts.forSession(lib, summary, turns.drop(summarizedCount)).takeIf { fits(llm, it) }
            ?: FilingPrompts.forSession(lib, summary, turns.drop(summarizedCount).takeLast(12))

    private fun fits(llm: LanguageModel, messages: List<ChatMessage>) =
        llm.count(llm.format.render(messages, true)) + MAX_TOKENS <= llm.contextSize

    private companion object {
        const val MAX_TOKENS = 1200
    }
}
