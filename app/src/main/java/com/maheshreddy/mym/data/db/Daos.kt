package com.maheshreddy.mym.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    fun observe(id: Long): Flow<SubjectEntity?>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun get(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE slug = :slug")
    suspend fun getBySlug(slug: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics WHERE subjectId = :subjectId ORDER BY title COLLATE NOCASE")
    fun observeForSubject(subjectId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE subjectId = :subjectId")
    suspend fun getForSubject(subjectId: Long): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE subjectId IS NULL ORDER BY title COLLATE NOCASE")
    fun observeUnassigned(): Flow<List<TopicEntity>>

    @Delete
    suspend fun delete(topic: TopicEntity)

    /** Topics with no entries at all: none filed and none proposed in the Inbox. */
    @Query("SELECT * FROM topics WHERE NOT EXISTS (SELECT 1 FROM entries e WHERE e.topicId = topics.id OR e.proposedTopicId = topics.id) ORDER BY title COLLATE NOCASE")
    fun observeEmpty(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics")
    suspend fun getAll(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE id = :id")
    fun observe(id: Long): Flow<TopicEntity?>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun get(id: Long): TopicEntity?

    @Query("SELECT * FROM topics WHERE slug = :slug")
    suspend fun getBySlug(slug: String): TopicEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(topic: TopicEntity): Long

    @Update
    suspend fun update(topic: TopicEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    /** Open sessions with no turns (abandoned before anything was said), started before [before]. */
    @Query("DELETE FROM sessions WHERE endedAt IS NULL AND startedAt < :before AND NOT EXISTS (SELECT 1 FROM turns t WHERE t.sessionId = sessions.id)")
    suspend fun deleteEmptyOpen(before: Long): Int

    /** An open, still-empty session started after [after]: reused instead of creating a duplicate. */
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND startedAt >= :after AND NOT EXISTS (SELECT 1 FROM turns t WHERE t.sessionId = sessions.id) ORDER BY startedAt DESC LIMIT 1")
    suspend fun reusableEmpty(after: Long): SessionEntity?

    /** Open sessions that have turns but no activity since [before] (shown in the Inbox to wrap up). */
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND lastActivityAt < :before AND EXISTS (SELECT 1 FROM turns t WHERE t.sessionId = sessions.id) ORDER BY lastActivityAt DESC")
    fun observeStaleOpen(before: Long): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM turns WHERE sessionId = :id")
    suspend fun turnCount(id: Long): Int

    @Query("UPDATE sessions SET lastActivityAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("SELECT * FROM sessions WHERE notesStatus IN ('PENDING', 'FAILED')")
    suspend fun pendingNotes(): List<SessionEntity>

    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Update
    suspend fun update(session: SessionEntity)
}

@Dao
interface TurnDao {
    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY id")
    fun observeForSession(sessionId: Long): Flow<List<TurnEntity>>

    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getForSession(sessionId: Long): List<TurnEntity>

    @Insert
    suspend fun insert(turn: TurnEntity): Long
}

@Dao
interface EntryDao {
    /** Filed-entry counts per topic, type and privacy (Library spines, Home cards, privacy filter). */
    @Query("SELECT topicId, type, privacy, COUNT(*) AS n FROM entries WHERE status = 'FILED' AND topicId IS NOT NULL GROUP BY topicId, type, privacy")
    fun observeTopicCounts(): Flow<List<TopicEntryCount>>

    /** Inbox entries proposed for each topic (so a topic whose entries wait in the Inbox does not read as empty). */
    @Query("SELECT proposedTopicId AS topicId, COUNT(*) AS n FROM entries WHERE status = 'INBOX' AND proposedTopicId IS NOT NULL GROUP BY proposedTopicId")
    fun observeWaitingCounts(): Flow<List<TopicWaiting>>

    /** The oldest filed entry (spaced-review nudge). */
    @Query("SELECT * FROM entries WHERE status = 'FILED' ORDER BY createdAt, id LIMIT 1")
    fun observeOldestFiled(): Flow<EntryEntity?>

    @Query("SELECT * FROM entries WHERE status = 'INBOX' ORDER BY createdAt DESC, id")
    fun observeInbox(): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entries WHERE status = 'INBOX'")
    fun observeInboxCount(): Flow<Int>

    /** "Filed today": entries filed without confirmation since [since], newest first. */
    @Query("SELECT * FROM entries WHERE status = 'FILED' AND autoFiled = 1 AND filedAt >= :since ORDER BY filedAt DESC, id")
    fun observeAutoFiledSince(since: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE topicId = :topicId AND status = 'FILED' ORDER BY createdAt, id")
    fun observeForTopic(topicId: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE topicId = :topicId AND status = 'FILED' ORDER BY createdAt, id")
    suspend fun filedForTopic(topicId: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE sessionId = :sessionId ORDER BY id")
    fun observeForSession(sessionId: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE topicId = :topicId")
    suspend fun allForTopic(topicId: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE status = 'FILED'")
    suspend fun allFiled(): List<EntryEntity>

    @Query("SELECT * FROM entries")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observe(id: Long): Flow<EntryEntity?>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: Long): EntryEntity?

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VectorDao {
    @Query("SELECT * FROM entry_vectors WHERE model = :model")
    suspend fun allFor(model: String): List<EntryVectorEntity>

    @Query("SELECT * FROM entry_vectors WHERE entryId = :entryId")
    suspend fun get(entryId: Long): EntryVectorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(v: EntryVectorEntity)

    @Query("SELECT COUNT(*) FROM entry_vectors WHERE model = :model")
    fun observeCount(model: String): Flow<Int>

    @Query("DELETE FROM entry_vectors WHERE model != :model")
    suspend fun dropOtherModels(model: String)
}
