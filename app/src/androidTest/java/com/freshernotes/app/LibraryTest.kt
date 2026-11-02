package com.freshernotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshernotes.app.data.LibraryException
import com.freshernotes.app.data.LibraryRepository
import com.freshernotes.app.data.Pick
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.db.AppDatabase
import com.freshernotes.app.data.db.NotesStatus
import com.freshernotes.app.data.settings.SettingsRepository
import com.freshernotes.app.engine.Engines
import com.freshernotes.app.library.SessionFiler
import com.freshernotes.app.models.ModelStore
import com.freshernotes.app.notes.MarkdownExporter
import com.freshernotes.app.search.DocKind
import com.freshernotes.app.search.SearchIndex
import com.freshernotes.core.Speaker
import com.freshernotes.core.library.EntryType
import com.freshernotes.core.library.Privacy
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.LanguageModel
import com.freshernotes.core.llm.PromptFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Wrap-up filing, the Inbox, maintenance operations, FTS5 search and export, with a scripted LLM. */
@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var index: SearchIndex
    private lateinit var library: LibraryRepository
    private lateinit var sessions: SessionRepository

    private class ScriptedModel(private val reply: String) : LanguageModel {
        override val contextSize = 4096
        override val format = PromptFormat.CHATML
        override fun count(text: String) = text.length / 4
        override fun stream(messages: List<ChatMessage>, maxTokens: Int, temperature: Float): Flow<String> = flowOf(reply)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        index = SearchIndex.inMemory()
        library = LibraryRepository(db, index)
        sessions = SessionRepository(db.sessionDao(), db.turnDao())
    }

    @After
    fun tearDown() = db.close()

    private fun filer() = SessionFiler(sessions, library, SettingsRepository(context), Engines(context, ModelStore(context)), index, com.freshernotes.app.data.ButlerRepository(context))

    private suspend fun session(vararg said: String): Long {
        val id = sessions.startSession("Test session")
        said.forEach { sessions.addTurn(id, Speaker.SENIOR, it) }
        sessions.endSession(id)
        return id
    }

    @Test
    fun fts5IndexSearchesWithPrefixesAndRanking() = runBlocking {
        index.upsert(DocKind.TOPIC, 1, "Drill jigs", "Drill jigs locate holes using hardened bushes.")
        index.upsert(DocKind.TOPIC, 2, "Teamcenter", "Workflows release items in Teamcenter.")
        index.upsert(DocKind.ENTRY, 3, "Entry", "We talked about bush wear on drill templates.")
        assertEquals(setOf(1L, 3L), index.search("bush").map { it.refId }.toSet())
        assertEquals(listOf(2L), index.search("teamcent").map { it.refId })
        assertTrue(index.search("\"; DROP TABLE docs; --").isEmpty())
    }

    @Test
    fun wrapUpFilesConfidentEntriesAndSendsTheRestToTheInbox() = runBlocking {
        // An existing, confirmed subject and topic.
        val plm = library.createSubject("PLM")
        library.accept(
            library.file(null, listOf(seedDecision())).single(),
            Pick.Existing(plm), Pick.New("Engineering change orders"),
        )
        val id = session("ECOs without a reason code get rejected.", "I also want to try a standing desk.", "Wrap up.")
        val model = ScriptedModel(
            """
            === ENTRY
            SUBJECT: PLM
            TOPIC: Engineering change orders
            TYPE: Lesson
            CONFIDENCE: high
            PRIVACY: Shareable
            TITLE: Reason codes on ECOs
            - ECOs without a reason code get rejected.
            === ENTRY
            SUBJECT: General
            TOPIC: Misc
            TYPE: Idea
            CONFIDENCE: high
            TITLE: Standing desk
            - Try a standing desk.
            """.trimIndent(),
        )
        val summary = filer().file(id, model).getOrThrow()
        assertEquals(1, summary.filed)
        assertEquals(1, summary.inbox)
        assertEquals(NotesStatus.DONE, sessions.getSession(id)!!.notesStatus)

        val filed = library.observeFiledToday().first().single()
        assertEquals("Reason codes on ECOs", filed.title)
        assertEquals("model cannot loosen the Private default", Privacy.PRIVATE.name, filed.privacy)
        val inbox = library.observeInbox().first().single()
        assertNull("catch-all proposal dropped", inbox.proposedSubject)
        assertNull(inbox.proposedTopic)

        val topic = db.topicDao().get(filed.topicId!!)!!
        assertTrue(topic.markdown, topic.markdown.contains("## Lessons") && topic.markdown.contains("ECOs without a reason code"))
        assertEquals(listOf(filed.id), index.search("reason code").filter { it.kind == DocKind.ENTRY }.map { it.refId }.filter { it == filed.id })

        // Filing again (e.g. after a crash) does not duplicate.
        sessions.setNotes(id, NotesStatus.PENDING)
        filer().file(id, model).getOrThrow()
        assertEquals(2, library.observeSessionEntries(id).first().size)
    }

    @Test
    fun unparseableOutputStillKeepsTheSession() = runBlocking {
        val id = session("Datums come from the master model.")
        filer().file(id, ScriptedModel("Sorry, I cannot help.")).getOrThrow()
        val e = library.observeInbox().first().single()
        assertTrue(e.body.contains("Datums come from the master model."))
    }

    @Test
    fun inboxAcceptCreatesConfirmedSubjectAndTopicThenUndoAndReject() = runBlocking {
        val (a, b) = library.file(null, listOf(seedDecision("A"), seedDecision("B")))
        val topicId = library.accept(a, Pick.New("Life"), Pick.New("Morning routine"), EntryType.REFLECTION, Privacy.PERSONAL)
        assertEquals("Life", db.subjectDao().getAll().single().title)
        val e = library.entry(a)!!
        assertEquals("FILED", e.status)
        assertEquals(EntryType.REFLECTION.name, e.type)
        assertFalse("user-confirmed entries are not in Filed today", e.autoFiled)
        assertTrue(db.topicDao().get(topicId)!!.markdown.contains("### 1. A"))

        library.undoFiling(a)
        assertEquals("INBOX", library.entry(a)!!.status)
        assertEquals(topicId, library.entry(a)!!.proposedTopicId)
        assertTrue(db.topicDao().get(topicId)!!.markdown.contains("_No entries yet._"))

        library.deleteEntry(b)
        assertNull(library.entry(b))

        try {
            library.accept(a, Pick.New("General"), Pick.New("Stuff"))
            fail("catch-all names must be refused")
        } catch (expected: LibraryException) {
        }
    }

    @Test
    fun moveMergeRenameDeleteKeepPagesCurrentAndLoseNothing() = runBlocking {
        val (a, b, c) = library.file(null, listOf(seedDecision("A"), seedDecision("B"), seedDecision("C")))
        val t1 = library.accept(a, Pick.New("PLM"), Pick.New("Releases"))
        val plm = db.subjectDao().getAll().single().id
        val t2 = library.accept(b, Pick.Existing(plm), Pick.New("Release states"))
        library.accept(c, Pick.Existing(plm), Pick.Existing(t2))

        // Split: move C into a new topic.
        library.moveEntry(c, Pick.Existing(plm), Pick.New("Effectivity"))
        val t3 = library.entry(c)!!.topicId!!
        assertFalse(db.topicDao().get(t2)!!.markdown.contains("C"))
        assertTrue(db.topicDao().get(t3)!!.markdown.contains("### 1. C"))

        // Merge t2 into t1.
        library.mergeTopics(t2, t1)
        assertNull(db.topicDao().get(t2))
        assertEquals(t1, library.entry(b)!!.topicId)
        assertTrue(db.topicDao().get(t1)!!.markdown.contains("### 2. B"))

        // Rename; a clash is refused.
        library.renameTopic(t1, "Release management")
        assertTrue(db.topicDao().get(t1)!!.markdown.startsWith("# Release management"))
        try { library.renameTopic(t3, "release management"); fail() } catch (expected: LibraryException) {}

        // Delete a topic: its entries go to the Inbox, not away.
        library.deleteTopic(t1)
        assertEquals(setOf(a, b), library.observeInbox().first().map { it.id }.toSet())
        assertEquals("Release management", library.entry(a)!!.proposedTopic)

        // Delete the subject: topics stay, unassigned.
        library.deleteSubject(plm)
        assertNull(db.topicDao().get(t3)!!.subjectId)
        assertEquals(listOf(t3), library.observeUnassignedTopics().first().map { it.id })
    }

    @Test
    fun deletingAJunkSessionRemovesEverything() = runBlocking {
        val id = session("Testing one two.")
        filer().file(id, ScriptedModel("=== ENTRY\nSUBJECT: Life\nTOPIC: Testing\nTYPE: Idea\nTITLE: Test\n- testing one two")).getOrThrow()
        assertEquals(1, library.observeSessionEntries(id).first().size)
        assertTrue(index.search("testing").isNotEmpty())
        library.deleteSessionCompletely(id)
        assertNull(sessions.getSession(id))
        assertTrue(sessions.turns(id).isEmpty())
        assertTrue(library.observeInbox().first().isEmpty())
        assertTrue(index.search("testing").isEmpty())
    }

    @Test
    fun markdownExportWritesThePage() = runBlocking {
        val uri = MarkdownExporter(context).export("library-test", "# Page\n- a")
        assertEquals("# Page\n- a", context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
    }

    private fun seedDecision(title: String = "Seed") = com.freshernotes.core.library.FilingDecision(
        status = com.freshernotes.core.library.EntryStatus.INBOX, subjectId = null, topicId = null,
        proposedSubject = null, proposedTopic = null, type = EntryType.KNOWLEDGE, privacy = Privacy.PRIVATE,
        title = title, body = "- body of $title", confidence = 0.2, reason = "test",
    )
}
