package com.maheshreddy.mym.core.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalTest {
    private fun v(vararg x: Float): FloatArray {
        val n = Math.sqrt(x.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(x.size) { x[it] / n }
    }

    private val datum = Candidate(1, Privacy.PRIVATE, v(1f, 0.1f, 0f))
    private val fixture = Candidate(2, Privacy.SHAREABLE, v(0.8f, 0.6f, 0f))
    private val cooking = Candidate(3, Privacy.PERSONAL, v(0f, 0f, 1f))
    private val noVector = Candidate(4, Privacy.PRIVATE, null)
    private val all = listOf(datum, fixture, cooking, noVector)

    @Test
    fun meaningFindsEntriesWithoutSharedWords() {
        val hits = Retrieval.rank(v(1f, 0.2f, 0f), all, keywordIds = emptyList())
        assertEquals(listOf(1L, 2L), hits.map { it.entryId })
        assertTrue(hits.none { it.entryId == 3L })
    }

    @Test
    fun keywordAndMeaningAreFused() {
        // Entry 4 has no vector but matches the words; entry 2 matches both, so it ranks first.
        val hits = Retrieval.rank(v(0.8f, 0.6f, 0f), all, keywordIds = listOf(2, 4))
        assertEquals(2L, hits.first().entryId)
        assertTrue(hits.any { it.entryId == 4L })
    }

    @Test
    fun nothingRelevantReturnsEmpty() {
        assertTrue(Retrieval.rank(v(0f, 1f, 0f), listOf(datum, cooking), keywordIds = emptyList()).isEmpty())
    }

    @Test
    fun privacyFilterIsRespected() {
        val shareableOnly = Retrieval.rank(v(1f, 0.2f, 0f), all, keywordIds = listOf(1, 4), allowed = setOf(Privacy.SHAREABLE))
        assertEquals(listOf(2L), shareableOnly.map { it.entryId })
    }

    @Test
    fun keywordOnlyWhenNoEmbeddingModel() {
        assertEquals(listOf(4L, 1L), Retrieval.rank(null, all, keywordIds = listOf(4, 1)).map { it.entryId })
    }

    @Test
    fun vectorsRoundTripThroughBytes() {
        val x = floatArrayOf(0.25f, -1.5f, 3f)
        assertArrayEquals(x, Retrieval.fromBytes(Retrieval.toBytes(x)), 0f)
    }

    @Test
    fun suggestionsComeFromTopics() {
        val s = Retrieval.suggestions(listOf("RFP to kickoff" to 1, "Drill jigs" to 0), listOf("Project Management"))
        assertEquals("What is still open on RFP to kickoff?", s[0])
        assertTrue(s.contains("What have I said about Drill jigs?"))
        assertTrue(s.size in 3..4)
        assertTrue(Retrieval.suggestions(emptyList(), emptyList()).isEmpty())
    }
}

class StatsTest {
    private val zone = java.time.ZoneOffset.UTC
    private val day = 86_400_000L
    private val now = 20 * day + 3_600_000L

    @Test
    fun streakAndWeek() {
        val created = listOf(now, now - day, now - 2 * day, now - 2 * day, now - 4 * day, now - 10 * day)
        val s = Stats.of(created, topics = 3, now = now, zone = zone)
        assertEquals(3, s.streakDays)
        assertEquals(5, s.entriesThisWeek)
        assertEquals(3, s.topics)
    }

    @Test
    fun streakSurvivesUntilTodaysSession() {
        assertEquals(2, Stats.of(listOf(now - day, now - 2 * day), 0, now, zone).streakDays)
        assertEquals(0, Stats.of(listOf(now - 3 * day), 0, now, zone).streakDays)
        assertEquals(0, Stats.of(emptyList(), 0, now, zone).entriesThisWeek)
    }
}

class GapsTest {
    @Test
    fun suggestsMissingVocabularyOpenQuestionsAndThinTopics() {
        val g = Gaps.suggest(
            listOf("ECO", "datum", "jig", "BOM"),
            listOf("Drill jigs use hardened bushes", "Pick the datum faces first"),
            listOf(Triple("Drill jigs", 3, 1), Triple("Trade-offs", 1, 0)),
            limit = 4,
        )
        assertEquals(
            listOf("No entries on ECO yet", "Drill jigs: an open question to settle", "Only one entry on Trade-offs", "No entries on BOM yet"),
            g.map { it.text },
        )
        assertEquals("ECO", g[0].talkAbout)
    }

    @Test
    fun progressCountsSolidTopics() {
        assertEquals(0.5f, Gaps.progress(listOf(3 to 0, 1 to 0)), 0f)
        assertEquals(0f, Gaps.progress(emptyList()), 0f)
    }
}
