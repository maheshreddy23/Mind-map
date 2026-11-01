package com.freshernotes.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshernotes.app.data.SessionRepository
import com.freshernotes.app.data.db.AppDatabase
import com.freshernotes.core.Speaker
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun turnsArePersistedInOrder() = runTest {
        val repo = SessionRepository(db.sessionDao(), db.turnDao())
        val id = repo.startSession("Test")
        repo.addTurn(id, Speaker.SENIOR, "We use jigs to hold the part.")
        repo.addTurn(id, Speaker.FRESHER, "Why not clamp it directly?")
        val turns = repo.turns(id)
        assertEquals(listOf(Speaker.SENIOR, Speaker.FRESHER), turns.map { it.speaker })
        repo.endSession(id)
        assertNotNull(repo.getSession(id)?.endedAt)
    }
}
