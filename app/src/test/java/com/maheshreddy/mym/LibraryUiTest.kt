package com.maheshreddy.mym

import com.maheshreddy.mym.data.db.SubjectEntity
import com.maheshreddy.mym.data.db.TopicEntity
import com.maheshreddy.mym.data.db.TopicEntryCount
import com.maheshreddy.mym.ui.home.HomeViewModel
import com.maheshreddy.mym.ui.library.LibraryUi
import com.maheshreddy.mym.core.library.Privacy
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryUiTest {
    private val subjects = listOf(SubjectEntity(1, "plm", "PLM"), SubjectEntity(2, "life", "Life"))
    private val topics = listOf(
        TopicEntity(10, "eco", "ECOs", subjectId = 1),
        TopicEntity(11, "boms", "BOMs", subjectId = 1),
        TopicEntity(20, "walks", "Walks", subjectId = 2),
    )
    private val counts = listOf(
        TopicEntryCount(10, "KNOWLEDGE", "PRIVATE", 2),
        TopicEntryCount(10, "OPEN_QUESTION", "SHAREABLE", 1),
        TopicEntryCount(20, "REFLECTION", "PERSONAL", 1),
    )

    @Test
    fun tilesCountEntriesAndOpenQuestions() {
        val t = LibraryUi.tiles(topics, subjects, counts).associateBy { it.id }
        assertEquals(3, t.getValue(10).entries)
        assertEquals(1, t.getValue(10).openQuestions)
        assertEquals("PLM", t.getValue(10).subject)
        assertEquals(0, t.getValue(11).entries)
        assertEquals("3 entries · 1 open", LibraryUi.caption(t.getValue(10)))
        assertEquals("0 entries", LibraryUi.caption(t.getValue(11)))
    }

    @Test
    fun privacyFilterKeepsOnlyMatchingTopicsAndCounts() {
        val shareable = LibraryUi.tiles(topics, subjects, counts, Privacy.SHAREABLE)
        assertEquals(listOf(10L), shareable.map { it.id })
        assertEquals(1, shareable.single().entries)
        val shelves = LibraryUi.shelves(subjects, topics, shareable, Privacy.SHAREABLE)
        assertEquals(listOf("PLM"), shelves.map { it.title })
        // Unfiltered, empty subjects are still shown (as empty shelves).
        assertEquals(2, LibraryUi.shelves(subjects, topics, LibraryUi.tiles(topics, subjects, counts), null).size)
    }

    @Test
    fun filingHeadlineReadsNaturally() {
        assertEquals("I filed two entries. One needs your call.", LibraryUi.filingHeadline(2, 1))
        assertEquals("I filed one entry.", LibraryUi.filingHeadline(1, 0))
        assertEquals("I couldn't file anything with confidence. Two need your call.", LibraryUi.filingHeadline(0, 2))
        assertEquals("I filed 12 entries. Three need your call.", LibraryUi.filingHeadline(12, 3))
    }

    @Test
    fun greetingFollowsTheClock() {
        assertEquals("Good morning", HomeViewModel.greeting(8))
        assertEquals("Good afternoon", HomeViewModel.greeting(14))
        assertEquals("Good evening", HomeViewModel.greeting(21))
        assertEquals("Good evening", HomeViewModel.greeting(2))
    }
}
