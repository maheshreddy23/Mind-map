package com.freshernotes.app.data

import com.freshernotes.app.data.db.SessionDao
import com.freshernotes.app.data.db.NotesStatus
import com.freshernotes.app.data.db.SessionEntity
import com.freshernotes.app.data.db.TurnDao
import com.freshernotes.app.data.db.TurnEntity
import com.freshernotes.core.ChatTurn
import com.freshernotes.core.Speaker
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

    suspend fun startSession(title: String): Long = sessionDao.insert(SessionEntity(title = title))

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.get(id)

    /** Persists a turn immediately so nothing is lost if the app dies mid-session. */
    suspend fun addTurn(sessionId: Long, speaker: Speaker, text: String): Long =
        turnDao.insert(TurnEntity(sessionId = sessionId, speaker = speaker.name, text = text))

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

    suspend fun setNotes(sessionId: Long, status: String, markdown: String = "", topicId: Long? = null) {
        val s = sessionDao.get(sessionId) ?: return
        sessionDao.update(s.copy(notesStatus = status, notesMarkdown = markdown.ifEmpty { s.notesMarkdown }, topicId = topicId ?: s.topicId))
    }

    suspend fun pendingNotes(): List<SessionEntity> = sessionDao.pendingNotes()
}
