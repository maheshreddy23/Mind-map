package com.freshernotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.TopicRepository
import com.freshernotes.app.data.db.AppDatabase
import com.freshernotes.app.data.db.NotesStatus
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.models.ModelStore
import com.freshernotes.app.notes.MarkdownExporter
import com.freshernotes.app.notes.NoteGenerator
import com.freshernotes.app.search.DocKind
import com.freshernotes.app.search.SearchIndex
import com.freshernotes.core.Speaker
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.LanguageModel
import com.freshernotes.core.llm.PromptFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Session end -> notes -> topic merge -> FTS5 search -> Markdown export, with a scripted LLM. */
@RunWith(AndroidJUnit4::class)
class NotesPipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private val index = SearchIndex.inMemory()

    private class ScriptedModel(private val reply: String) : LanguageModel {
        override val contextSize = 4096
        override val format = PromptFormat.CHATML
        override fun count(text: String) = text.length / 4
        override fun stream(messages: List<ChatMessage>, maxTokens: Int, temperature: Float): Flow<String> = flowOf(reply)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun fts5IndexSearchesWithPrefixesAndRanking() = runBlocking {
        index.upsert(DocKind.TOPIC, 1, "Drill jigs", "Drill jigs locate holes using hardened bushes.")
        index.upsert(DocKind.TOPIC, 2, "Teamcenter", "Workflows release items in Teamcenter.")
        index.upsert(DocKind.SESSION, 3, "Session", "We talked about bush wear on drill templates.")
        val hits = index.search("bush")
        assertEquals(setOf(1L, 3L), hits.map { it.refId }.toSet())
        assertEquals(listOf(2L), index.search("teamcent").map { it.refId })
        assertTrue(index.search("\"; DROP TABLE docs; --").isEmpty())
        index.upsert(DocKind.TOPIC, 1, "Drill jigs", "Now about clamping only.")
        assertEquals(listOf(3L), index.search("bush").map { it.refId })
    }

    @Test
    fun wrapUpGeneratesMergesIndexesAndExports() = runBlocking {
        val sessions = SessionRepository(db.sessionDao(), db.turnDao())
        val topics = TopicRepository(db.topicDao())
        val generator = NoteGenerator(sessions, topics, Engines(context, ModelStore(context)), index, MarkdownExporter(context))

        // An existing page with a similar title must be merged into, not duplicated.
        val existing = topics.getOrCreate("Drill Jigs and Fixtures")
        val id = sessions.startSession("Jigs walkthrough")
        sessions.addTurn(id, Speaker.SENIOR, "Drill jigs are located on tooling holes.")
        sessions.addTurn(id, Speaker.FRESHER, "Why tooling holes?")
        sessions.addTurn(id, Speaker.SENIOR, "They are the datums for the whole assembly. Let's wrap up.")
        sessions.endSession(id)
        assertEquals(NotesStatus.PENDING, sessions.getSession(id)!!.notesStatus)

        val model = ScriptedModel(
            """
            TOPIC: Drill jig
            SUBTOPICS: tooling holes, datums
            ## Key concepts explained
            - Drill jigs are located on tooling holes.
            ## Procedures or steps
            1. Locate the jig on the tooling holes.
            ## Reasons and rationale
            - Tooling holes are the assembly datums.
            ## Pitfalls and lessons learned
            - none
            ## Open questions to revisit
            - What tolerance do the tooling holes have?
            """.trimIndent(),
        )
        val result = generator.generate(id, model)
        assertEquals(existing.id, result.getOrThrow())

        val page = topics.get(existing.id)!!.markdown
        assertTrue(page, page.startsWith("# Drill Jigs and Fixtures"))
        assertTrue(page.contains("- Tooling holes are the assembly datums."))
        assertTrue(page.contains("## Open questions to revisit\n- What tolerance"))
        assertEquals(1, topics.all().size)

        val session = sessions.getSession(id)!!
        assertEquals(NotesStatus.DONE, session.notesStatus)
        assertEquals(existing.id, session.topicId)

        assertEquals(listOf(existing.id), index.search("datums").filter { it.kind == DocKind.TOPIC }.map { it.refId })
        assertEquals(listOf(id), index.search("datums").filter { it.kind == DocKind.SESSION }.map { it.refId })

        val uri = MarkdownExporter(context).export(existing.slug, page)
        val written = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertEquals(page, written)
    }
}
