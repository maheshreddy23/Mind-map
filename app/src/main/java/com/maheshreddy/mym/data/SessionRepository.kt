package com.maheshreddy.mym.data

import com.maheshreddy.mym.data.db.SessionDao
import com.maheshreddy.mym.data.db.NotesStatus
import com.maheshreddy.mym.data.db.SessionEntity
import com.maheshreddy.mym.data.db.TurnDao
import com.maheshreddy.mym.data.db.TurnEntity
import com.maheshreddy.mym.core.ChatTurn
import com.maheshreddy.mym.core.Speaker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val turnDao: TurnDao,
) {
    fun recentSessions(limit: Int = 20): Flow<List<SessionEntity>> = sessionDao.observeRecent(limit)

    fun observeSession(id: Long): Flow<SessionEntity?> = sessionDao.observe(id)

    fun observeTurns(sessionId: Long): Flow<List<TurnEntity>> = turnDao.observeForSession(sessionId)

    suspend fun startSession(title: String, topicHint: String? = null): Long = sessionDao.insert(SessionEntity(title = title, topicHint = topicHint))

    /**
     * Starts a session, but reuses one opened in the last [REUSE_WINDOW_MS] that is still empty. A double tap on
     * Talk (or Talk while the previous navigation is still running) used to create two sessions in the same minute.
     */
    suspend fun startOrReuse(title: String, topicHint: String?, now: Long = System.currentTimeMillis()): Long {
        sessionDao.reusableEmpty(now - REUSE_WINDOW_MS)?.let { s ->
            if (topicHint != null && s.topicHint != topicHint) sessionDao.update(s.copy(title = title, topicHint = topicHint))
            return s.id
        }
        return startSession(title, topicHint)
    }

    /** Removes sessions that were opened but never used (no turns). Returns how many. */
    suspend fun deleteEmptySessions(olderThan: Long): Int = sessionDao.deleteEmptyOpen(olderThan)

    /** Deletes this session if nothing was said in it. */
    suspend fun deleteIfEmpty(id: Long) {
        val s = sessionDao.get(id) ?: return
        if (s.endedAt == null && sessionDao.turnCount(id) == 0) sessionDao.delete(id)
    }

    /** Open sessions with turns but idle for [IDLE_MS]: the Inbox offers to wrap them up. */
    fun staleOpenSessions(now: Long = System.currentTimeMillis()): Flow<List<SessionEntity>> = sessionDao.observeStaleOpen(now - IDLE_MS)

    suspend fun turnCount(id: Long) = sessionDao.turnCount(id)

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.get(id)

    /** Persists a turn immediately so nothing is lost if the app dies mid-session. */
    suspend fun addTurn(sessionId: Long, speaker: Speaker, text: String): Long {
        val id = turnDao.insert(TurnEntity(sessionId = sessionId, speaker = speaker.name, text = text))
        sessionDao.touch(sessionId, System.currentTimeMillis())
        return id
    }

    suspend fun turns(sessionId: Long): List<ChatTurn> =
        turnDao.getForSession(sessionId).map { ChatTurn(Speaker.valueOf(it.speaker), it.text) }

    suspend fun updateSummary(sessionId: Long, summary: String, summarizedTurnCount: Int) {
        val s = sessionDao.get(sessionId) ?: return
        sessionDao.update(s.copy(runningSummary = summary, summarizedTurnCount = summarizedTurnCount))
    }

    /** Marks the session ended and queues note generation. */
    suspend fun endSession(sessionId: Long) {
        val s = sessionDao.get(sessionId) ?: return
        sessionDao.update(s.copy(endedAt = s.endedAt ?: System.currentTimeMillis(), notesStatus = NotesStatus.PENDING))
    }

    suspend fun rename(sessionId: Long, title: String) {
        val s = sessionDao.get(sessionId) ?: return
        val t = title.trim().take(120)
        if (t.isNotEmpty()) sessionDao.update(s.copy(title = t))
    }

    suspend fun setNotes(sessionId: Long, status: String, summary: String? = null, topic: String? = null) {
        val s = sessionDao.get(sessionId) ?: return
        sessionDao.update(s.copy(notesStatus = status, summary = summary ?: s.summary, topicHint = s.topicHint ?: topic))
    }

    suspend fun pendingNotes(): List<SessionEntity> = sessionDao.pendingNotes()

    companion object {
        const val REUSE_WINDOW_MS = 2 * 60 * 1000L
        const val IDLE_MS = 12 * 60 * 60 * 1000L
    }
}
