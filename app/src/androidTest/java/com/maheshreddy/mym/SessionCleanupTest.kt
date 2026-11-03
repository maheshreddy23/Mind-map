package com.maheshreddy.mym

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maheshreddy.mym.data.LibraryRepository
import com.maheshreddy.mym.data.Pick
import com.maheshreddy.mym.data.SessionRepository
import com.maheshreddy.mym.data.db.AppDatabase
import com.maheshreddy.mym.data.db.SessionEntity
import com.maheshreddy.mym.search.SearchIndex
import com.maheshreddy.mym.core.Speaker
import com.maheshreddy.mym.core.library.EntryStatus
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.FilingDecision
import com.maheshreddy.mym.core.library.Privacy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The "stuck In progress", "duplicate sessions" and "topic with 0 entries" bugs. */
@RunWith(AndroidJUnit4::class)
class SessionCleanupTest {
    private lateinit var db: AppDatabase
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        sessions = SessionRepository(db.sessionDao(), db.turnDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun doubleTapReusesTheEmptySessionInsteadOfCreatingASecond() = runBlocking {
        val a = sessions.startOrReuse("Session 2 Nov, 06:06", null)
        val b = sessions.startOrReuse("Session 2 Nov, 06:06", null)
        assertEquals(a, b)
        assertEquals(1, db.sessionDao().getAll().size)
        // Once something was said, a new Talk starts a new session.
        sessions.addTurn(a, Speaker.SENIOR, "Hello")
        assertTrue(sessions.startOrReuse("Session 2 Nov, 06:07", null) != a)
    }

    @Test
    fun emptySessionsAreDeletedButUsedOnesAreKept() = runBlocking {
        val now = System.currentTimeMillis()
        val empty = db.sessionDao().insert(SessionEntity(title = "empty", startedAt = now - 3_600_000))
        val used = db.sessionDao().insert(SessionEntity(title = "used", startedAt = now - 3_600_000))
        sessions.addTurn(used, Speaker.SENIOR, "Something worth keeping")
        val fresh = db.sessionDao().insert(SessionEntity(title = "just opened", startedAt = now))
        assertEquals(1, sessions.deleteEmptySessions(now - 600_000))
        assertNull(sessions.getSession(empty))
        assertNotNull(sessions.getSession(used))
        assertNotNull("a session opened a moment ago is not swept", sessions.getSession(fresh))
        sessions.deleteIfEmpty(fresh)
        assertNull(sessions.getSession(fresh))
        sessions.deleteIfEmpty(used)
        assertNotNull(sessions.getSession(used))
    }

    @Test
    fun sessionsIdleFor12HoursAreOfferedForWrapUp() = runBlocking {
        val now = System.currentTimeMillis()
        val old = db.sessionDao().insert(SessionEntity(title = "old", startedAt = now - 20 * 3_600_000L, lastActivityAt = now - 20 * 3_600_000L))
        db.turnDao().insert(com.maheshreddy.mym.data.db.TurnEntity(sessionId = old, speaker = "SENIOR", text = "x"))
        val recent = sessions.startSession("recent")
        sessions.addTurn(recent, Speaker.SENIOR, "y")
        assertEquals(listOf(old), sessions.staleOpenSessions(now).first().map { it.id })
        sessions.endSession(old)
        assertTrue(sessions.staleOpenSessions(now).first().isEmpty())
    }

    @Test
    fun emptyTopicsAreListedAndCanBeDeleted() = runBlocking {
        val library = LibraryRepository(db, SearchIndex.inMemory(), com.maheshreddy.mym.search.EmbeddingIndexer(ApplicationProvider.getApplicationContext()))
        val d = FilingDecision(EntryStatus.INBOX, null, null, null, null, EntryType.KNOWLEDGE, Privacy.PRIVATE, "Mouse buttons", "- b", 0.2, "t")
        val id = library.file(null, listOf(d)).single()
        val topic = library.accept(id, Pick.New("Computers"), Pick.New("Benefits of Mouse Button Usage"))
        assertTrue(library.observeEmptyTopics().first().isEmpty())
        library.undoFiling(id) // entry back in the Inbox, proposed for the topic: not empty
        assertTrue(library.observeEmptyTopics().first().isEmpty())
        assertEquals(1, library.observeWaitingCounts().first().single().n)
        library.deleteEntry(id) // now nothing refers to it
        assertEquals(listOf(topic), library.observeEmptyTopics().first().map { it.id })
        library.deleteEmptyTopics(listOf(topic))
        assertNull(library.topic(topic))
    }
}
