package com.freshernotes.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshernotes.app.data.db.ALL_MIGRATIONS
import com.freshernotes.app.data.db.AppDatabase
import com.freshernotes.app.data.db.MIGRATION_2_3
import com.freshernotes.core.notes.NoteParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v1 app (database version 2) must update to v2 (version 3) with every session, turn and note kept.
 * The version-2 schema comes from app/schemas/.../2.json, exported by the v1 build on main.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun v1Note(concept: String, pitfall: String) = NoteParser.parse(
        "TOPIC: x\n## Key concepts explained\n- $concept\n## Pitfalls and lessons learned\n- $pitfall\n## Open questions to revisit\n- none",
    ).toMarkdown()

    @Test
    fun v1DataSurvivesAndIsSortedIntoTheLibrary() {
        helper.createDatabase(dbName, 2).use { db ->
            fun topic(id: Long, title: String, md: String) = db.insert(
                "topics", SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply { put("id", id); put("slug", title.lowercase().replace(' ', '-')); put("title", title); put("markdown", md); put("updatedAt", 1L) },
            )
            fun session(id: Long, title: String, topicId: Long?, notes: String, status: String = "DONE") = db.insert(
                "sessions", SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", id); put("title", title); put("startedAt", 1000L * id); put("endedAt", 1000L * id + 1)
                    put("runningSummary", ""); put("summarizedTurnCount", 0); put("notesStatus", status)
                    put("notesMarkdown", notes); put("topicId", topicId)
                },
            )
            fun turn(sessionId: Long, text: String) = db.insert(
                "turns", SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply { put("sessionId", sessionId); put("speaker", "SENIOR"); put("text", text); put("createdAt", 5L) },
            )
            topic(1, "Drill jigs", "# Drill jigs\nHAND EDIT: jig bushes, fixture datum, clamp")
            topic(2, "Weekend thoughts", "# Weekend thoughts\n- rest")
            session(1, "Jigs session", 1, v1Note("Jig bushes guide the drill on the datum.", "Worn bushes drift."))
            session(2, "Thinking", 2, v1Note("Rest matters.", "Burnout."))
            session(3, "Unfinished", null, "", status = "PENDING")
            turn(1, "Drill jigs use bushes."); turn(1, "Wrap up."); turn(2, "I rested."); turn(3, "Half a thought")
        }

        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).use { db ->
            fun count(sql: String) = db.query(sql).use { it.moveToFirst(); it.getInt(0) }
            assertEquals("sessions kept", 3, count("SELECT COUNT(*) FROM sessions"))
            assertEquals("turns kept", 4, count("SELECT COUNT(*) FROM turns"))
            assertEquals("v1 notes kept on the session", 2, count("SELECT COUNT(*) FROM sessions WHERE notesMarkdown != ''"))
            assertEquals("pending filing still pending", 1, count("SELECT COUNT(*) FROM sessions WHERE notesStatus = 'PENDING'"))

            // Confident topic: under the spec-named subject, entries filed and typed.
            assertEquals(1, count("SELECT COUNT(*) FROM subjects WHERE title = 'Jigs and Fixtures'"))
            assertEquals(1, count("SELECT COUNT(*) FROM topics t JOIN subjects s ON s.id = t.subjectId WHERE t.id = 1"))
            assertEquals(2, count("SELECT COUNT(*) FROM entries WHERE sessionId = 1 AND status = 'FILED' AND topicId = 1 AND autoFiled = 1"))
            assertEquals(1, count("SELECT COUNT(*) FROM entries WHERE sessionId = 1 AND type = 'LESSON'"))
            assertEquals("default privacy is Private", 0, count("SELECT COUNT(*) FROM entries WHERE privacy != 'PRIVATE'"))

            // Uncertain topic: no subject, entries in the Inbox pre-filled with the v1 topic.
            db.query("SELECT subjectId FROM topics WHERE id = 2").use { it.moveToFirst(); assertTrue(it.isNull(0)) }
            assertEquals(2, count("SELECT COUNT(*) FROM entries WHERE sessionId = 2 AND status = 'INBOX' AND proposedTopicId = 2 AND topicId IS NULL"))

            // No catch-all subject or topic was created.
            assertEquals(0, count("SELECT COUNT(*) FROM subjects WHERE lower(title) IN ('general','misc','other')"))

            // The v1 page, with its hand edit, is kept verbatim; the page is rebuilt from entries.
            db.query("SELECT legacyMarkdown, markdown FROM topics WHERE id = 1").use {
                it.moveToFirst()
                assertTrue(it.getString(0).contains("HAND EDIT"))
                assertTrue(it.getString(1), it.getString(1).contains("Jig bushes guide the drill"))
            }
        }

        // And Room itself opens the migrated file with every registered migration.
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName).addMigrations(*ALL_MIGRATIONS).build()
        try {
            runBlocking {
                assertEquals(3, room.sessionDao().getAll().size)
                assertEquals(1, room.subjectDao().getAll().size)
                assertNull(room.topicDao().get(2)!!.subjectId)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun emptyV1DatabaseMigrates() {
        helper.createDatabase(dbName, 2).close()
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM entries").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun everyVersionMigratesToTheLatest() {
        helper.createDatabase(dbName, 2).close()
        helper.runMigrationsAndValidate(dbName, 3, true, *ALL_MIGRATIONS)
    }
}
