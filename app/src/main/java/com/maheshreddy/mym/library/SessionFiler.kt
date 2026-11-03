package com.maheshreddy.mym.library

import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.data.SessionRepository
import com.maheshreddy.mym.data.db.NotesStatus
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.engine.LlamaLanguageModel
import com.maheshreddy.mym.search.DocKind
import com.maheshreddy.mym.search.SearchIndex
import com.maheshreddy.mym.core.ChatTurn
import com.maheshreddy.mym.core.Speaker
import com.maheshreddy.mym.core.library.EntryStatus
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.FilingDecision
import com.maheshreddy.mym.core.library.FilingParser
import com.maheshreddy.mym.core.library.FilingPolicy
import com.maheshreddy.mym.core.library.FilingPrompts
import com.maheshreddy.mym.core.library.SubjectRef
import com.maheshreddy.mym.core.llm.ChatMessage
import com.maheshreddy.mym.core.llm.ContextManager
import com.maheshreddy.mym.core.llm.LanguageModel
import com.maheshreddy.mym.core.llm.complete
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
    private val butler: com.maheshreddy.mym.data.ButlerRepository,
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
            val prefs = butler.journal.first()
            val raw = llm.complete(promptFor(llm, snapshot, prefs, session.runningSummary, session.summarizedTurnCount, turns), maxTokens = MAX_TOKENS, temperature = 0.2f)
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
    private fun fallback(title: String, raw: String, turns: List<ChatTurn>, privacy: com.maheshreddy.mym.core.library.Privacy): FilingDecision {
        val said = turns.filter { it.speaker == Speaker.SENIOR }.joinToString("\n") { "- " + it.text.trim() }
        return FilingDecision(
            status = EntryStatus.INBOX, subjectId = null, topicId = null, proposedSubject = null, proposedTopic = null,
            type = EntryType.KNOWLEDGE, privacy = privacy, title = title,
            body = raw.trim().takeIf { it.length > 40 } ?: said,
            confidence = 0.1, reason = "The butler could not split this session; please file it",
        )
    }

    /** Uses the full transcript when it fits; otherwise the running summary plus the turns after it. */
    private fun promptFor(llm: LanguageModel, lib: List<SubjectRef>, prefs: List<String>, summary: String, summarizedCount: Int, turns: List<ChatTurn>) =
        FilingPrompts.forSession(lib, "", turns, prefs).takeIf { fits(llm, it) }
            ?: FilingPrompts.forSession(lib, summary, turns.drop(summarizedCount), prefs).takeIf { fits(llm, it) }
            ?: FilingPrompts.forSession(lib, summary, turns.drop(summarizedCount).takeLast(12), prefs)

    private fun fits(llm: LanguageModel, messages: List<ChatMessage>) =
        llm.count(llm.format.render(messages, true)) + MAX_TOKENS <= llm.contextSize

    private companion object {
        const val MAX_TOKENS = 1200
    }
}
