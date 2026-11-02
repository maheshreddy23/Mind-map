package com.freshernotes.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.freshernotes.core.Slug
import com.freshernotes.core.library.EntryStatus
import com.freshernotes.core.library.EntryType
import com.freshernotes.core.library.LegacyPlanner
import com.freshernotes.core.library.LegacySession
import com.freshernotes.core.library.LegacyTopic
import com.freshernotes.core.library.PageBuilder
import com.freshernotes.core.library.PageEntry
import com.freshernotes.core.library.Privacy

/** Every migration, in order. Never add fallbackToDestructiveMigration: user data must survive every update. */
val ALL_MIGRATIONS: Array<Migration> by lazy { arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) }

/** v2: per-session notes and the topic they were merged into. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN notesStatus TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE sessions ADD COLUMN notesMarkdown TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sessions ADD COLUMN topicId INTEGER")
    }
}

/**
 * v3 (product v2): Subject → Topic → Entry, types, privacy and the Inbox.
 * Schema: new `subjects` and `entries` tables, three new `topics` columns. No v1 table is rebuilt or dropped.
 * Data: v1 session notes become typed entries (see [LegacyPlanner]); the v1 topic page is kept verbatim in
 * `topics.legacyMarkdown`; sessions and turns are untouched. Room runs this inside one transaction, so a crash
 * midway leaves the v2 database as it was.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subjects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `slug` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subjects_slug` ON `subjects` (`slug`)")

        db.execSQL("ALTER TABLE topics ADD COLUMN subjectId INTEGER")
        db.execSQL("ALTER TABLE topics ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE topics ADD COLUMN legacyMarkdown TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_topics_subjectId` ON `topics` (`subjectId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER, " +
                "`topicId` INTEGER, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `type` TEXT NOT NULL, `privacy` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `confidence` REAL NOT NULL, `proposedSubjectId` INTEGER, `proposedTopicId` INTEGER, " +
                "`proposedSubject` TEXT, `proposedTopic` TEXT, `reason` TEXT NOT NULL, `autoFiled` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `filedAt` INTEGER, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_sessionId` ON `entries` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_topicId` ON `entries` (`topicId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_status` ON `entries` (`status`)")

        db.execSQL("UPDATE topics SET legacyMarkdown = markdown")
        migrateV1Data(db, System.currentTimeMillis())
        repairCatchAllTopics(db, System.currentTimeMillis())
    }
}

/**
 * v4: data only, no schema change. Version 3 kept v1 topics named "General" (v1's fallback topic name), which
 * the spec forbids. Their entries go to the Inbox (no topic, the subject kept as a suggestion), the kept v1 page
 * becomes one Inbox entry so nothing is lost, and the catch-all topic is deleted. Sessions are untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        repairCatchAllTopics(db, System.currentTimeMillis())
    }
}

internal fun repairCatchAllTopics(db: SupportSQLiteDatabase, now: Long) {
    data class T(val id: Long, val title: String, val subjectId: Long?, val legacy: String)
    val catchAll = db.query("SELECT id, title, subjectId, legacyMarkdown FROM topics").use { c ->
        buildList { while (c.moveToNext()) add(T(c.getLong(0), c.getString(1), if (c.isNull(2)) null else c.getLong(2), c.getString(3) ?: "")) }
    }.filter { com.freshernotes.core.library.CatchAll.isCatchAll(it.title) }
    for (t in catchAll) {
        val subject = t.subjectId?.let { id ->
            db.query("SELECT title FROM subjects WHERE id = ?", arrayOf<Any>(id)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }
        val reason = "Was under the catch-all topic \"${t.title}\"; choose a real topic"
        db.update(
            "entries", SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("status", EntryStatus.INBOX.name); putNull("topicId"); put("autoFiled", 0); putNull("filedAt")
                putNull("proposedTopicId"); putNull("proposedTopic"); put("proposedSubjectId", t.subjectId); put("proposedSubject", subject)
                put("reason", reason); put("updatedAt", now)
            },
            "topicId = ? OR proposedTopicId = ?", arrayOf<Any>(t.id, t.id),
        )
        if (t.legacy.isNotBlank()) {
            db.insert(
                "entries", SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    putNull("sessionId"); putNull("topicId"); put("title", "Earlier page: ${t.title}"); put("body", t.legacy)
                    put("type", EntryType.KNOWLEDGE.name); put("privacy", Privacy.DEFAULT.name); put("status", EntryStatus.INBOX.name)
                    put("confidence", 0.1); put("proposedSubjectId", t.subjectId); put("proposedSubject", subject)
                    put("reason", "The v1 page of the catch-all topic \"${t.title}\", kept so nothing is lost. Reject it if the entries cover it.")
                    put("autoFiled", 0); put("source", EntrySource.MIGRATED); put("createdAt", now); put("updatedAt", now)
                },
            )
        }
        db.delete("topics", "id = ?", arrayOf<Any>(t.id))
    }
}

internal fun migrateV1Data(db: SupportSQLiteDatabase, now: Long) {
    val topics = db.query("SELECT id, title, markdown FROM topics").use { c ->
        buildList { while (c.moveToNext()) add(LegacyTopic(c.getLong(0), c.getString(1), c.getString(2) ?: "")) }
    }
    val sessions = db.query("SELECT id, title, startedAt, topicId, notesMarkdown FROM sessions").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(LegacySession(c.getLong(0), c.getString(1), c.getLong(2), if (c.isNull(3)) null else c.getLong(3), c.getString(4) ?: ""))
            }
        }
    }
    val plan = LegacyPlanner.plan(topics, sessions)

    val subjectIds = HashMap<String, Long>()
    for (title in plan.subjects) {
        subjectIds[title] = db.insert(
            "subjects", SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("slug", Slug.of(title)); put("title", title); put("createdAt", now); put("updatedAt", now)
            },
        )
    }
    for ((topicId, subject) in plan.topicSubjects) {
        db.update("topics", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply { put("subjectId", subjectIds.getValue(subject)) }, "id = ?", arrayOf(topicId))
    }
    val titles = topics.associate { it.id to it.title }
    for (e in plan.entries) {
        val filed = e.status == EntryStatus.FILED
        db.insert(
            "entries", SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("sessionId", e.sessionId)
                put("topicId", if (filed) e.topicId else null)
                put("title", e.title); put("body", e.body); put("type", e.type.name)
                put("privacy", Privacy.DEFAULT.name); put("status", e.status.name); put("confidence", e.confidence)
                put("proposedSubjectId", e.proposedSubject?.let { subjectIds[it] })
                put("proposedTopicId", if (filed) null else e.topicId)
                put("proposedSubject", e.proposedSubject)
                put("proposedTopic", e.topicId?.let { titles[it] })
                put("reason", e.reason); put("autoFiled", if (filed) 1 else 0); put("source", EntrySource.MIGRATED)
                put("createdAt", e.createdAt.takeIf { it > 0 } ?: now); put("updatedAt", now)
                put("filedAt", if (filed) now else null)
            },
        )
    }
    // Rebuild each topic page from its filed entries; the v1 page itself stays in legacyMarkdown.
    val subjectTitles = subjectIds.entries.associate { it.value to it.key }
    for (t in topics) {
        val subjectId = plan.topicSubjects[t.id]?.let { subjectIds[it] }
        val entries = db.query("SELECT id, title, type, body, createdAt FROM entries WHERE topicId = ? AND status = 'FILED'", arrayOf(t.id)).use { c ->
            buildList { while (c.moveToNext()) add(PageEntry(c.getLong(0), c.getString(1), EntryType.valueOf(c.getString(2)), c.getString(3), c.getLong(4))) }
        }
        val page = PageBuilder.build(t.title, subjectId?.let { subjectTitles[it] }, "", entries)
        db.update("topics", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply { put("markdown", page) }, "id = ?", arrayOf(t.id))
    }
}
