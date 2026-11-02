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
import com.freshernotes.app.data.db.MIGRATION_3_4
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

    /** The phone's case: v3 kept a v1 "General" topic under a subject; v4 empties and removes it, losing nothing. */
    @Test
    fun catchAllTopicIsRepairedWithoutLosingAnything() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL("INSERT INTO subjects (id, slug, title, createdAt, updatedAt) VALUES (1, 'project-management', 'Project Management', 1, 1)")
            db.execSQL("INSERT INTO topics (id, slug, title, markdown, updatedAt, subjectId, summary, legacyMarkdown) VALUES (5, 'general', 'General', '', 1, 1, '', 'HAND EDIT v1 page')")
            db.execSQL("INSERT INTO topics (id, slug, title, markdown, updatedAt, subjectId, summary, legacyMarkdown) VALUES (6, 'kickoff', 'Kickoff', '', 1, 1, '', '')")
            db.execSQL("INSERT INTO sessions (id, title, startedAt, endedAt, runningSummary, summarizedTurnCount, notesStatus, notesMarkdown, topicId) VALUES (1, 's', 1, 2, '', 0, 'DONE', 'n', 5)")
            fun entry(id: Int, topic: String, status: String) = db.execSQL(
                "INSERT INTO entries (id, sessionId, topicId, title, body, type, privacy, status, confidence, reason, autoFiled, source, createdAt, updatedAt) " +
                    "VALUES ($id, 1, $topic, 'e$id', '- b', 'KNOWLEDGE', 'PRIVATE', '$status', 0.8, '', 1, 'MIGRATED', 1, 1)",
            )
            entry(1, "5", "FILED"); entry(2, "5", "FILED"); entry(3, "6", "FILED")
        }
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).use { db ->
            fun count(sql: String) = db.query(sql).use { it.moveToFirst(); it.getInt(0) }
            assertEquals("catch-all topic gone", 0, count("SELECT COUNT(*) FROM topics WHERE lower(title) = 'general'"))
            assertEquals("other topics untouched", 1, count("SELECT COUNT(*) FROM entries WHERE id = 3 AND topicId = 6 AND status = 'FILED'"))
            assertEquals("its entries wait in the Inbox", 2, count("SELECT COUNT(*) FROM entries WHERE id IN (1, 2) AND status = 'INBOX' AND topicId IS NULL AND proposedSubjectId = 1"))
            assertEquals("v1 page kept as an Inbox entry", 1, count("SELECT COUNT(*) FROM entries WHERE status = 'INBOX' AND body LIKE '%HAND EDIT%'"))
            assertEquals("session kept", 1, count("SELECT COUNT(*) FROM sessions"))
        }
    }

    @Test
    fun v1GeneralTopicNeverSurvivesTheV2Migration() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO topics (id, slug, title, markdown, updatedAt) VALUES (1, 'general', 'General', '# General\n- jig fixture datum bush clamp', 1)")
            db.execSQL("INSERT INTO sessions (id, title, startedAt, endedAt, runningSummary, summarizedTurnCount, notesStatus, notesMarkdown, topicId) VALUES (1, 's', 1, 2, '', 0, 'DONE', '${v1Note("Jig bushes and datums.", "Worn bushes.")}', 1)")
        }
        helper.runMigrationsAndValidate(dbName, 4, true, *ALL_MIGRATIONS).use { db ->
            fun count(sql: String) = db.query(sql).use { it.moveToFirst(); it.getInt(0) }
            assertEquals(0, count("SELECT COUNT(*) FROM topics"))
            assertEquals(0, count("SELECT COUNT(*) FROM entries WHERE status = 'FILED'"))
            assertTrue(count("SELECT COUNT(*) FROM entries WHERE status = 'INBOX' AND sessionId = 1") >= 2)
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
        helper.runMigrationsAndValidate(dbName, 4, true, *ALL_MIGRATIONS)
    }
}
