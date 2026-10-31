package com.freshernotes.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "topics", indices = [Index(value = ["slug"], unique = true)])
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val title: String,
    /** The merged Markdown knowledge page for this topic. */
    val markdown: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

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
)

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
