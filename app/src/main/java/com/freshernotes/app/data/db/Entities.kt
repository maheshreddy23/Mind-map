package com.freshernotes.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** v2 §6.1: a broad area, for example PLM or Life. Created only after the user confirms it. */
@Entity(tableName = "subjects", indices = [Index(value = ["slug"], unique = true)])
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * A book within a subject. [subjectId] is null only for v1 topics whose subject the migration could not
 * decide; they are listed under "Needs a subject" until the user picks one. No foreign key, so v2 could add the
 * column without rebuilding the v1 table; [com.freshernotes.app.data.LibraryRepository] keeps it consistent.
 */
@Entity(tableName = "topics", indices = [Index(value = ["slug"], unique = true), Index("subjectId")])
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val title: String,
    /** The topic page, rebuilt from its filed entries by PageBuilder after every change. */
    val markdown: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val subjectId: Long? = null,
    /** Optional prose overview written by the LLM ("Rewrite summary"); shown at the top of the page. */
    @ColumnInfo(defaultValue = "") val summary: String = "",
    /** The v1 merged page as it was before the v2 migration, kept verbatim (it may hold hand edits). */
    @ColumnInfo(defaultValue = "") val legacyMarkdown: String = "",
)

/** v2 §6.1-6.3: one captured piece. Filed entries have a [topicId]; Inbox entries carry a proposal instead. */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("sessionId"), Index("topicId"), Index("status")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long? = null,
    /** Set when FILED. */
    val topicId: Long? = null,
    val title: String,
    /** Markdown bullets. */
    val body: String,
    /** [com.freshernotes.core.library.EntryType] name. */
    val type: String,
    /** [com.freshernotes.core.library.Privacy] name. */
    val privacy: String,
    /** [com.freshernotes.core.library.EntryStatus] name: FILED or INBOX. */
    val status: String,
    /** 0..1 filing confidence. */
    val confidence: Double = 0.0,
    /** Inbox proposal: an existing subject/topic id when the names matched, else just the proposed names. */
    val proposedSubjectId: Long? = null,
    val proposedTopicId: Long? = null,
    val proposedSubject: String? = null,
    val proposedTopic: String? = null,
    /** Why it is in the Inbox. */
    val reason: String = "",
    /** True when filed without the user's confirmation; these appear in "Filed today" with Undo. */
    val autoFiled: Boolean = false,
    /** SESSION, TYPED or MIGRATED; see [EntrySource]. */
    val source: String = EntrySource.SESSION,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val filedAt: Long? = null,
)

object EntrySource {
    const val SESSION = "SESSION"
    const val TYPED = "TYPED"
    const val MIGRATED = "MIGRATED"
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    /** Rolling summary of turns that were compressed out of the LLM context window. */
    val runningSummary: String = "",
    /** Number of turns (from the start) already folded into [runningSummary]. */
    val summarizedTurnCount: Int = 0,
    /** NONE, PENDING, DONE or FAILED; see [NotesStatus]. */
    @ColumnInfo(defaultValue = "NONE") val notesStatus: String = NotesStatus.NONE,
    /** Notes generated for this session alone (also merged into the topic page). */
    @ColumnInfo(defaultValue = "") val notesMarkdown: String = "",
    val topicId: Long? = null,
)

object NotesStatus {
    const val NONE = "NONE"
    const val PENDING = "PENDING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

@Entity(
    tableName = "turns",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** SENIOR, FRESHER or SYSTEM, see [com.freshernotes.core.Speaker]. */
    val speaker: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)
