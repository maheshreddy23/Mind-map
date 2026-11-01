package com.freshernotes.app.notes

import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.TopicRepository
import com.freshernotes.app.data.db.NotesStatus
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.engine.LlamaLanguageModel
import com.freshernotes.app.search.DocKind
import com.freshernotes.app.search.SearchIndex
import com.freshernotes.core.ChatTurn
import com.freshernotes.core.Speaker
import com.freshernotes.core.llm.ContextManager
import com.freshernotes.core.llm.LanguageModel
import com.freshernotes.core.llm.complete
import com.freshernotes.core.notes.NoteParser
import com.freshernotes.core.notes.NotePrompts
import com.freshernotes.core.notes.TopicMatcher
import com.freshernotes.core.notes.TopicPageMerger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session end: LLM -> structured notes -> topic match -> merge into the topic page -> search index -> Markdown file.
 * Sessions are marked PENDING first, so generation resumes on next launch if the app is killed midway.
 */
@Singleton
class NoteGenerator @Inject constructor(
    private val sessions: SessionRepository,
    private val topics: TopicRepository,
    private val engines: Engines,
    private val index: SearchIndex,
    private val exporter: MarkdownExporter,
) {
    private val mutex = Mutex()

    suspend fun generatePending() {
        for (s in sessions.pendingNotes()) generate(s.id)
    }

    suspend fun generate(sessionId: Long, model: LanguageModel? = null): Result<Long> = mutex.withLock {
        try {
            val session = requireNotNull(sessions.getSession(sessionId)) { "session $sessionId not found" }
            val turns = sessions.turns(sessionId)
            if (turns.none { it.speaker == Speaker.SENIOR }) {
                sessions.setNotes(sessionId, NotesStatus.DONE)
                return Result.success(-1)
            }
            val llm = model ?: LlamaLanguageModel(engines.llm())
            val raw = llm.complete(promptFor(llm, session.runningSummary, session.summarizedTurnCount, turns), maxTokens = 900, temperature = 0.2f)
            val note = NoteParser.parse(raw)

            val existingTitles = topics.allTitles()
            val title = TopicMatcher.bestMatch(note.topic, existingTitles) ?: note.topic
            val topic = topics.getOrCreate(title)
            val label = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(session.startedAt)) + " — " + session.title
            val page = TopicPageMerger.merge(topic.markdown, topic.title, note, label)
            topics.saveMarkdown(topic.id, page)
            sessions.setNotes(sessionId, NotesStatus.DONE, note.toMarkdown(), topic.id)

            index.upsert(DocKind.TOPIC, topic.id, topic.title, page)
            index.upsert(DocKind.SESSION, sessionId, session.title, ContextManager.transcript(turns))
            runCatching { exporter.export(topic.slug, page) }
            Result.success(topic.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sessions.setNotes(sessionId, NotesStatus.FAILED)
            Result.failure(e)
        }
    }

    /** Uses the full transcript when it fits; otherwise the running summary plus the turns after it. */
    private fun promptFor(llm: LanguageModel, summary: String, summarizedCount: Int, turns: List<ChatTurn>) =
        NotePrompts.forSession("", turns).takeIf { fits(llm, it) }
            ?: NotePrompts.forSession(summary, turns.drop(summarizedCount)).takeIf { fits(llm, it) }
            ?: NotePrompts.forSession(summary, turns.drop(summarizedCount).takeLast(12))

    private fun fits(llm: LanguageModel, messages: List<com.freshernotes.core.llm.ChatMessage>) =
        llm.count(llm.format.render(messages, true)) + 900 <= llm.contextSize
}
