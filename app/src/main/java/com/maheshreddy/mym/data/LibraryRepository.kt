package com.maheshreddy.mym.data

import androidx.room.withTransaction
import com.maheshreddy.mym.data.db.AppDatabase
import com.maheshreddy.mym.data.db.EntryEntity
import com.maheshreddy.mym.data.db.EntrySource
import com.maheshreddy.mym.data.db.SubjectEntity
import com.maheshreddy.mym.data.db.TopicEntity
import com.maheshreddy.mym.search.DocKind
import com.maheshreddy.mym.search.SearchIndex
import com.maheshreddy.mym.core.Slug
import com.maheshreddy.mym.core.library.CatchAll
import com.maheshreddy.mym.core.library.EntryStatus
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.FilingDecision
import com.maheshreddy.mym.core.library.PageBuilder
import com.maheshreddy.mym.core.library.PageEntry
import com.maheshreddy.mym.core.library.Privacy
import com.maheshreddy.mym.core.library.SubjectRef
import com.maheshreddy.mym.core.library.TopicRef
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** A subject or topic the user picked: an existing one, or a new name (choosing a new name is the confirmation). */
sealed interface Pick {
    data class Existing(val id: Long) : Pick
    data class New(val name: String) : Pick
}

class LibraryException(message: String) : Exception(message)

/**
 * All Subject → Topic → Entry operations (v2 §6). Every structural change runs in one Room transaction and then
 * rebuilds the affected topic pages and search documents, so pages are never stale.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val db: AppDatabase,
    private val index: SearchIndex,
) {
    private val subjects = db.subjectDao()
    private val topics = db.topicDao()
    private val entries = db.entryDao()
    private val sessions = db.sessionDao()

    // ---- Observing ----
    fun observeSubjects(): Flow<List<SubjectEntity>> = subjects.observeAll()
    fun observeSubject(id: Long): Flow<SubjectEntity?> = subjects.observe(id)
    fun observeTopics(subjectId: Long): Flow<List<TopicEntity>> = topics.observeForSubject(subjectId)
    fun observeAllTopics(): Flow<List<TopicEntity>> = topics.observeAll()
    fun observeUnassignedTopics(): Flow<List<TopicEntity>> = topics.observeUnassigned()
    fun observeTopic(id: Long): Flow<TopicEntity?> = topics.observe(id)
    fun observeTopicEntries(topicId: Long): Flow<List<EntryEntity>> = entries.observeForTopic(topicId)
    fun observeEntry(id: Long): Flow<EntryEntity?> = entries.observe(id)
    fun observeInbox(): Flow<List<EntryEntity>> = entries.observeInbox()
    fun observeEmptyTopics(): Flow<List<TopicEntity>> = topics.observeEmpty()
    fun observeWaitingCounts(): Flow<List<com.maheshreddy.mym.data.db.TopicWaiting>> = entries.observeWaitingCounts()

    /** Deletes the given topics if they are still empty (no filed or proposed entries). */
    suspend fun deleteEmptyTopics(ids: Collection<Long>) {
        val removed = db.withTransaction {
            ids.mapNotNull { id ->
                val t = topics.get(id) ?: return@mapNotNull null
                if (entries.allForTopic(id).isNotEmpty() || entries.getAll().any { it.proposedTopicId == id }) return@mapNotNull null
                topics.delete(t); id
            }
        }
        removed.forEach { index.remove(DocKind.TOPIC, it) }
    }

    fun observeOldestFiled(): Flow<EntryEntity?> = entries.observeOldestFiled()
    fun observeTopicCounts(): Flow<List<com.maheshreddy.mym.data.db.TopicEntryCount>> = entries.observeTopicCounts()
    fun observeInboxCount(): Flow<Int> = entries.observeInboxCount()
    fun observeSessionEntries(sessionId: Long): Flow<List<EntryEntity>> = entries.observeForSession(sessionId)
    fun observeFiledToday(now: Long = System.currentTimeMillis()): Flow<List<EntryEntity>> = entries.observeAutoFiledSince(startOfDay(now))

    suspend fun subject(id: Long) = subjects.get(id)
    suspend fun topic(id: Long) = topics.get(id)
    suspend fun entry(id: Long) = entries.get(id)
    suspend fun allSubjects() = subjects.getAll()
    suspend fun topicsOf(subjectId: Long) = topics.getForSubject(subjectId)

    /** The confirmed library, as the filing prompt and policy see it. */
    suspend fun snapshot(): List<SubjectRef> = subjects.getAll().map { s ->
        SubjectRef(s.id, s.title, topics.getForSubject(s.id).map { TopicRef(it.id, it.title) })
    }

    // ---- Filing ----

    /** Stores the filer's decisions for one session (or typed note). Returns the new entry ids. */
    suspend fun file(sessionId: Long?, decisions: List<FilingDecision>, source: String = EntrySource.SESSION): List<Long> {
        val now = System.currentTimeMillis()
        val ids = db.withTransaction {
            decisions.map { d ->
                val filed = d.status == EntryStatus.FILED && d.topicId != null
                entries.insert(
                    EntryEntity(
                        sessionId = sessionId,
                        topicId = if (filed) d.topicId else null,
                        title = d.title, body = d.body, type = d.type.name, privacy = d.privacy.name,
                        status = if (filed) EntryStatus.FILED.name else EntryStatus.INBOX.name,
                        confidence = d.confidence,
                        proposedSubjectId = if (filed) null else d.subjectId,
                        proposedTopicId = if (filed) null else d.topicId,
                        proposedSubject = d.proposedSubject, proposedTopic = d.proposedTopic,
                        reason = d.reason, autoFiled = filed, source = source,
                        createdAt = now, updatedAt = now, filedAt = if (filed) now else null,
                    ),
                )
            }
        }
        refreshTopics(decisions.filter { it.status == EntryStatus.FILED }.mapNotNull { it.topicId }.toSet())
        ids.forEach { indexEntry(it) }
        return ids
    }

    /** Inbox → filed. A [Pick.New] subject or topic is created here: picking it is the user's confirmation. */
    suspend fun accept(
        entryId: Long,
        subject: Pick,
        topic: Pick,
        type: EntryType? = null,
        privacy: Privacy? = null,
    ): Long {
        val topicId = db.withTransaction {
            val e = entries.get(entryId) ?: throw LibraryException("Entry not found")
            val t = resolveTopic(resolveSubject(subject), topic)
            val now = System.currentTimeMillis()
            entries.update(
                e.copy(
                    topicId = t.id, status = EntryStatus.FILED.name, autoFiled = false, filedAt = now, updatedAt = now,
                    type = type?.name ?: e.type, privacy = privacy?.name ?: e.privacy, reason = "",
                    proposedSubjectId = null, proposedTopicId = null, confidence = 1.0,
                ),
            )
            t.id
        }
        refreshTopics(setOf(topicId))
        indexEntry(entryId)
        return topicId
    }

    /** "Filed today" Undo: back to the Inbox, with the place it was filed as the proposal. */
    suspend fun undoFiling(entryId: Long) {
        val oldTopic = db.withTransaction {
            val e = entries.get(entryId) ?: return@withTransaction null
            val t = e.topicId?.let { topics.get(it) }
            val s = t?.subjectId?.let { subjects.get(it) }
            entries.update(
                e.copy(
                    status = EntryStatus.INBOX.name, topicId = null, autoFiled = false, filedAt = null, updatedAt = System.currentTimeMillis(),
                    proposedTopicId = t?.id, proposedTopic = t?.title ?: e.proposedTopic,
                    proposedSubjectId = s?.id, proposedSubject = s?.title ?: e.proposedSubject,
                    reason = "Filing undone",
                ),
            )
            t?.id
        }
        refreshTopics(setOfNotNull(oldTopic))
    }

    /** Reject from the Inbox, or delete a filed entry. */
    suspend fun deleteEntry(entryId: Long) {
        val e = entries.get(entryId) ?: return
        entries.delete(entryId)
        index.remove(DocKind.ENTRY, entryId)
        refreshTopics(setOfNotNull(e.topicId))
    }

    suspend fun updateEntry(entryId: Long, title: String, body: String, type: EntryType, privacy: Privacy) {
        val e = entries.get(entryId) ?: return
        entries.update(e.copy(title = title.trim().ifEmpty { e.title }, body = body.trim(), type = type.name, privacy = privacy.name, updatedAt = System.currentTimeMillis()))
        refreshTopics(setOfNotNull(e.topicId))
        indexEntry(entryId)
    }

    /** Move a filed entry to another topic; with [Pick.New] this is how a topic is split. */
    suspend fun moveEntry(entryId: Long, subject: Pick, topic: Pick) {
        val (from, to) = db.withTransaction {
            val e = entries.get(entryId) ?: throw LibraryException("Entry not found")
            val t = resolveTopic(resolveSubject(subject), topic)
            entries.update(e.copy(topicId = t.id, status = EntryStatus.FILED.name, updatedAt = System.currentTimeMillis(), filedAt = e.filedAt ?: System.currentTimeMillis()))
            e.topicId to t.id
        }
        refreshTopics(setOfNotNull(from, to))
        indexEntry(entryId)
    }

    // ---- Subjects and topics ----

    suspend fun createSubject(name: String): Long = db.withTransaction { resolveSubject(Pick.New(name)).id }

    suspend fun renameSubject(id: Long, name: String) {
        val clean = validName(name)
        val s = subjects.get(id) ?: return
        subjects.getBySlug(Slug.of(clean))?.takeIf { it.id != id }?.let { throw LibraryException("\"${it.title}\" already exists. Merge instead.") }
        subjects.update(s.copy(slug = Slug.of(clean), title = clean, updatedAt = System.currentTimeMillis()))
        refreshTopics(topics.getForSubject(id).map { it.id }.toSet())
    }

    /** Moves every topic of [sourceId] into [targetId], then deletes the source subject. */
    suspend fun mergeSubjects(sourceId: Long, targetId: Long) {
        if (sourceId == targetId) return
        val moved = db.withTransaction {
            val source = subjects.get(sourceId) ?: return@withTransaction emptyList()
            subjects.get(targetId) ?: throw LibraryException("Target subject not found")
            val list = topics.getForSubject(sourceId)
            list.forEach { topics.update(it.copy(subjectId = targetId)) }
            subjects.delete(source)
            list.map { it.id }
        }
        refreshTopics(moved.toSet())
    }

    /** Deletes the subject only; its topics stay, listed under "Needs a subject". */
    suspend fun deleteSubject(id: Long) {
        val affected = db.withTransaction {
            val s = subjects.get(id) ?: return@withTransaction emptyList()
            val list = topics.getForSubject(id)
            list.forEach { topics.update(it.copy(subjectId = null)) }
            subjects.delete(s)
            list.map { it.id }
        }
        refreshTopics(affected.toSet())
    }

    suspend fun renameTopic(id: Long, name: String) {
        val clean = validName(name)
        val t = topics.get(id) ?: return
        topics.getBySlug(Slug.of(clean))?.takeIf { it.id != id }?.let { throw LibraryException("\"${it.title}\" already exists. Merge instead.") }
        topics.update(t.copy(slug = Slug.of(clean), title = clean, updatedAt = System.currentTimeMillis()))
        refreshTopics(setOf(id))
    }

    suspend fun moveTopic(topicId: Long, subject: Pick) {
        db.withTransaction {
            val t = topics.get(topicId) ?: return@withTransaction
            topics.update(t.copy(subjectId = resolveSubject(subject).id, updatedAt = System.currentTimeMillis()))
        }
        refreshTopics(setOf(topicId))
    }

    /** Moves every entry (and Inbox proposal) of [sourceId] into [targetId], deletes the source. */
    suspend fun mergeTopics(sourceId: Long, targetId: Long) {
        if (sourceId == targetId) return
        db.withTransaction {
            val source = topics.get(sourceId) ?: return@withTransaction
            val target = topics.get(targetId) ?: throw LibraryException("Target topic not found")
            entries.allForTopic(sourceId).forEach { entries.update(it.copy(topicId = targetId, updatedAt = System.currentTimeMillis())) }
            entries.getAll().filter { it.proposedTopicId == sourceId }.forEach { entries.update(it.copy(proposedTopicId = targetId, proposedTopic = target.title)) }
            topics.update(target.copy(subjectId = target.subjectId ?: source.subjectId))
            topics.delete(source)
        }
        index.remove(DocKind.TOPIC, sourceId)
        refreshTopics(setOf(targetId))
    }

    /**
     * Deletes a topic. With [deleteEntries] false (the default), its entries go back to the Inbox so nothing is
     * lost; with true they are deleted too.
     */
    suspend fun deleteTopic(id: Long, deleteEntries: Boolean = false) {
        val removed = db.withTransaction {
            val t = topics.get(id) ?: return@withTransaction emptyList()
            val s = t.subjectId?.let { subjects.get(it) }
            val list = entries.allForTopic(id)
            for (e in list) {
                if (deleteEntries) entries.delete(e.id)
                else entries.update(
                    e.copy(
                        status = EntryStatus.INBOX.name, topicId = null, autoFiled = false, filedAt = null,
                        proposedTopicId = null, proposedTopic = t.title, proposedSubjectId = s?.id, proposedSubject = s?.title,
                        reason = "Its topic \"${t.title}\" was deleted",
                    ),
                )
            }
            entries.getAll().filter { it.proposedTopicId == id }.forEach { entries.update(it.copy(proposedTopicId = null)) }
            topics.delete(t)
            if (deleteEntries) list.map { it.id } else emptyList()
        }
        index.remove(DocKind.TOPIC, id)
        removed.forEach { index.remove(DocKind.ENTRY, it) }
    }

    /** v2 §6.4: removes a test or junk session completely: its turns, its entries and its search documents. */
    suspend fun deleteSessionCompletely(sessionId: Long) {
        val (affected, entryIds) = db.withTransaction {
            val list = entries.forSession(sessionId)
            list.forEach { entries.delete(it.id) }
            sessions.delete(sessionId) // turns cascade
            list.mapNotNull { it.topicId }.toSet() to list.map { it.id }
        }
        index.remove(DocKind.SESSION, sessionId)
        entryIds.forEach { index.remove(DocKind.ENTRY, it) }
        refreshTopics(affected)
    }

    // ---- Pages and search ----

    /** Rebuilds the page (and search document) of each topic from its filed entries. */
    suspend fun refreshTopics(ids: Set<Long>) {
        for (id in ids) {
            val t = topics.get(id) ?: continue
            val subject = t.subjectId?.let { subjects.get(it) }?.title
            val page = PageBuilder.build(
                t.title, subject, t.summary,
                entries.filedForTopic(id).map { PageEntry(it.id, it.title, EntryType.valueOf(it.type), it.body, it.createdAt) },
            )
            topics.update(t.copy(markdown = page, updatedAt = System.currentTimeMillis()))
            index.upsert(DocKind.TOPIC, id, t.title, page)
        }
    }

    suspend fun setSummary(topicId: Long, summary: String) {
        val t = topics.get(topicId) ?: return
        topics.update(t.copy(summary = summary.trim()))
        refreshTopics(setOf(topicId))
    }


    private suspend fun indexEntry(id: Long) {
        val e = entries.get(id) ?: return
        index.upsert(DocKind.ENTRY, id, e.title, e.body)
    }

    /** Rebuilds the whole search index from Room (after the v2 migration, or if the index is lost). */
    suspend fun rebuildIndex(transcript: suspend (Long) -> String) {
        index.clear()
        topics.getAll().forEach { index.upsert(DocKind.TOPIC, it.id, it.title, it.markdown) }
        entries.getAll().forEach { index.upsert(DocKind.ENTRY, it.id, it.title, it.body) }
        sessions.getAll().forEach { s -> transcript(s.id).takeIf { it.isNotBlank() }?.let { index.upsert(DocKind.SESSION, s.id, s.title, it) } }
    }

    // ---- Helpers (call inside a transaction) ----

    private suspend fun resolveSubject(pick: Pick): SubjectEntity = when (pick) {
        is Pick.Existing -> subjects.get(pick.id) ?: throw LibraryException("Subject not found")
        is Pick.New -> {
            val name = validName(pick.name)
            subjects.getBySlug(Slug.of(name)) ?: subjects.get(subjects.insert(SubjectEntity(slug = Slug.of(name), title = name)))!!
        }
    }

    private suspend fun resolveTopic(subject: SubjectEntity, pick: Pick): TopicEntity = when (pick) {
        is Pick.Existing -> {
            val t = topics.get(pick.id) ?: throw LibraryException("Topic not found")
            // A v1 topic without a subject takes the one the user just chose.
            if (t.subjectId == null) t.copy(subjectId = subject.id).also { topics.update(it) } else t
        }
        is Pick.New -> {
            val name = validName(pick.name)
            val existing = topics.getBySlug(Slug.of(name))
            when {
                existing == null -> topics.get(topics.insert(TopicEntity(slug = Slug.of(name), title = name, subjectId = subject.id)))!!
                existing.subjectId == null -> existing.copy(subjectId = subject.id).also { topics.update(it) }
                else -> existing
            }
        }
    }

    private fun validName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw LibraryException("Name cannot be empty")
        if (CatchAll.isCatchAll(clean)) throw LibraryException("\"$clean\" is a catch-all. Use a specific name.")
        return clean
    }

    companion object {
        fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
