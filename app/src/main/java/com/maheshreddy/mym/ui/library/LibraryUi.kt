package com.maheshreddy.mym.ui.library

import com.maheshreddy.mym.data.db.SubjectEntity
import com.maheshreddy.mym.data.db.TopicEntity
import com.maheshreddy.mym.data.db.TopicEntryCount
import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.Privacy

/** A topic as a spine or card: real counts from filed entries. */
data class TopicTile(
    val id: Long,
    val title: String,
    val subject: String?,
    val entries: Int,
    val openQuestions: Int,
    /** Privacy levels present among its entries. */
    val privacies: Set<Privacy>,
    /** Entries proposed for this topic that still wait in the Inbox (not yet counted in [entries]). */
    val waiting: Int = 0,
)

data class Shelf(val subjectId: Long, val title: String, val topics: List<TopicTile>)

/** Pure builders shared by Home and Library (kept free of Android types for tests). */
object LibraryUi {
    fun tiles(
        topics: List<TopicEntity>,
        subjects: List<SubjectEntity>,
        counts: List<TopicEntryCount>,
        filter: Privacy? = null,
        waiting: List<com.maheshreddy.mym.data.db.TopicWaiting> = emptyList(),
    ): List<TopicTile> {
        val names = subjects.associate { it.id to it.title }
        val waitingBy = waiting.associate { it.topicId to it.n }
        val byTopic = counts.groupBy { it.topicId }
        return topics.mapNotNull { t ->
            val rows = byTopic[t.id].orEmpty()
            val visible = if (filter == null) rows else rows.filter { it.privacy == filter.name }
            if (filter != null && visible.isEmpty()) return@mapNotNull null
            TopicTile(
                id = t.id, title = t.title, subject = t.subjectId?.let { names[it] },
                entries = visible.sumOf { it.n },
                openQuestions = visible.filter { it.type == EntryType.OPEN_QUESTION.name }.sumOf { it.n },
                privacies = rows.mapNotNull { r -> runCatching { Privacy.valueOf(r.privacy) }.getOrNull() }.toSet(),
                waiting = waitingBy[t.id] ?: 0,
            )
        }
    }

    fun shelves(subjects: List<SubjectEntity>, topics: List<TopicEntity>, tiles: List<TopicTile>, filter: Privacy?): List<Shelf> {
        val byId = tiles.associateBy { it.id }
        return subjects.map { s ->
            Shelf(s.id, s.title, topics.filter { it.subjectId == s.id }.mapNotNull { byId[it.id] })
        }.filter { filter == null || it.topics.isNotEmpty() }
    }

    fun caption(t: TopicTile): String = buildString {
        append(plural(t.entries, "entry", "entries"))
        if (t.openQuestions > 0) append(" · ").append(t.openQuestions).append(" open")
        if (t.waiting > 0) append(" · ").append(t.waiting).append(" in Inbox")
    }

    fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    private val words = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    fun word(n: Int) = words.getOrNull(n) ?: n.toString()

    /** Wrap-up headline, design-spec §4: "I filed {n} entries. {m} need your call." */
    fun filingHeadline(filed: Int, waiting: Int): String {
        val first = when (filed) {
            0 -> "I couldn't file anything with confidence."
            1 -> "I filed one entry."
            else -> "I filed ${word(filed)} entries."
        }
        val second = when (waiting) {
            0 -> ""
            1 -> " One needs your call."
            else -> " ${word(waiting).replaceFirstChar { it.uppercase() }} need your call."
        }
        return first + second
    }
}
