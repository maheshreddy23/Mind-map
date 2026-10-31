package com.freshernotes.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
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
