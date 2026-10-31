package com.freshernotes.app.data

import com.freshernotes.app.data.db.TopicDao
import com.freshernotes.app.data.db.TopicEntity
import com.freshernotes.core.Slug
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopicRepository @Inject constructor(private val topicDao: TopicDao) {
    fun topics(): Flow<List<TopicEntity>> = topicDao.observeAll()

    fun observe(id: Long): Flow<TopicEntity?> = topicDao.observe(id)

    suspend fun saveMarkdown(id: Long, markdown: String) {
        val t = topicDao.get(id) ?: return
        topicDao.update(t.copy(markdown = markdown, updatedAt = System.currentTimeMillis()))
    }

    /** Returns the topic with this title's slug, creating it if needed. */
    suspend fun getOrCreate(title: String): TopicEntity {
        val slug = Slug.of(title)
        topicDao.getBySlug(slug)?.let { return it }
        val id = topicDao.insert(TopicEntity(slug = slug, title = title.trim()))
        return requireNotNull(topicDao.get(id))
    }
}
