package com.maheshreddy.mym.core.map

import com.maheshreddy.mym.core.library.EntryType
import com.maheshreddy.mym.core.library.Privacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class KnowledgeMapTest {
    private val day = 86_400_000L
    private val subjects = listOf(MapSubject(1, "Jigs and Fixtures"), MapSubject(2, "PLM"), MapSubject(3, "Personal Finance"))
    private val topics = listOf(
        MapTopicIn(10, 1, "Drill jigs"),
        MapTopicIn(11, 1, "Datum selection"),
        MapTopicIn(20, 2, "Teamcenter change orders"),
        MapTopicIn(21, 2, "Empty topic"),
    )
    private fun e(t: Long, text: String, at: Long = 10 * day, p: Privacy = Privacy.PRIVATE, type: EntryType = EntryType.KNOWLEDGE) = MapEntryIn(t, at, p, type, text)
    private val entries = listOf(
        e(10, "Drill bushes guide the drill; worn bushes make hole positions drift", at = 1 * day),
        e(10, "Locate the part on its datum faces before drilling holes", at = 2 * day),
        e(10, "Check drill bush wear every batch", at = 3 * day, p = Privacy.SHAREABLE),
        e(11, "Datum faces: locate from the mating faces, drill holes after", at = 4 * day, type = EntryType.OPEN_QUESTION),
        e(20, "ECO approval in Teamcenter needs design, quality and manufacturing sign-off", at = 20 * day),
    )

    @Test
    fun clustersNodesAndGaps() {
        val m = KnowledgeMap.build(subjects, topics, entries)
        assertEquals(3, m.clusters.size)
        assertTrue("Personal Finance has no topics: an empty-subject gap", m.clusters.single { it.subject.id == 3L }.empty)
        val drill = m.nodes.single { it.id == 10L }
        assertEquals(3, drill.entries)
        assertFalse(drill.gap)
        assertTrue("open question → gap", m.nodes.single { it.id == 11L }.gap)
        assertTrue("one entry → gap", m.nodes.single { it.id == 20L }.gap)
        assertTrue("no entries → gap", m.nodes.single { it.id == 21L }.gap)
        assertTrue(drill.r > m.nodes.single { it.id == 20L }.r)
        assertEquals(1 * day, drill.addedAt)
        assertEquals(4, m.gaps)
    }

    @Test
    fun clustersDoNotOverlapAndNodesStayInside() {
        val many = (1L..9L).map { MapSubject(it, "S$it") }
        val ts = (1L..9L).flatMap { s -> (0 until s.toInt()).map { MapTopicIn(s * 100 + it, s, "T$s-$it") } }
        val es = ts.map { MapEntryIn(it.id, 0, Privacy.PRIVATE, EntryType.KNOWLEDGE, it.title) }
        val m = KnowledgeMap.build(many, ts, es)
        for (a in m.clusters) for (b in m.clusters) if (a.index < b.index) {
            val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
            assertTrue("clusters ${a.index},${b.index} overlap", d >= a.r + b.r)
        }
        for (n in m.nodes) {
            val c = m.clusters[n.cluster]
            assertTrue(sqrt((n.x - c.x) * (n.x - c.x) + (n.y - c.y) * (n.y - c.y)) + n.r <= c.r)
        }
    }

    @Test
    fun linksFromSharedTermsAndConfirmation() {
        val m = KnowledgeMap.build(subjects, topics, entries)
        val l = m.links.single { it.a == 10L && it.b == 11L }
        assertEquals(LinkState.SUGGESTED, l.state)
        assertTrue(m.links.none { it.a == 10L && it.b == 20L })
        val confirmed = KnowledgeMap.build(subjects, topics, entries, confirmed = setOf(10L to 11L, 11L to 20L))
        assertEquals(LinkState.CONFIRMED, confirmed.links.single { it.a == 10L && it.b == 11L }.state)
        assertNotNull("a confirmed link shows even without shared terms", confirmed.links.singleOrNull { it.a == 11L && it.b == 20L })
        val dismissed = KnowledgeMap.build(subjects, topics, entries, dismissed = setOf(10L to 11L))
        assertTrue(dismissed.links.none { it.a == 10L && it.b == 11L })
    }

    @Test
    fun linksFromEmbeddings() {
        val links = KnowledgeMap.links(
            mapOf(1L to setOf("alpha"), 2L to setOf("beta"), 3L to setOf("gamma")),
            mapOf(1L to floatArrayOf(1f, 0.1f), 2L to floatArrayOf(0.95f, 0.2f), 3L to floatArrayOf(0f, 1f)),
            emptySet(), emptySet(),
        )
        assertEquals(listOf(1L to 2L), links.map { it.a to it.b })
    }

    @Test
    fun privacyAndTimeFilters() {
        val shareable = KnowledgeMap.build(subjects, topics, entries, privacy = setOf(Privacy.SHAREABLE))
        assertEquals(listOf(10L), shareable.nodes.map { it.id })
        assertEquals(1, shareable.nodes.single().entries)
        val recent = KnowledgeMap.build(subjects, topics, entries, since = 15 * day)
        assertEquals(listOf(20L), recent.nodes.map { it.id })
    }

    @Test
    fun emptyLibraryShowsStartingSubjects() {
        val m = KnowledgeMap.build(emptyList(), emptyList(), emptyList(), starters = listOf("Jigs and Fixtures", "PLM", "Life"))
        assertEquals(listOf("Jigs and Fixtures", "PLM", "Life"), m.clusters.map { it.subject.title })
        assertTrue(m.clusters.all { it.empty && it.subject.id == null })
        assertTrue(m.isEmpty)
    }

    @Test
    fun hitTesting() {
        val m = KnowledgeMap.build(subjects, topics, entries)
        val drill = m.nodes.single { it.id == 10L }
        assertEquals(10L, (KnowledgeMap.hit(m, drill.x + 2, drill.y - 2) as MapHit.Topic).node.id)
        val fin = m.clusters.single { it.subject.id == 3L }
        assertEquals(3L, (KnowledgeMap.hit(m, fin.x, fin.y) as MapHit.Subject).cluster.subject.id)
        val datum = m.nodes.single { it.id == 11L }
        val mid = KnowledgeMap.hit(m, (drill.x + datum.x) / 2, (drill.y + datum.y) / 2)
        assertTrue("midpoint of a link hits the link, got $mid", mid is MapHit.Link)
        assertEquals(null, KnowledgeMap.hit(m, m.maxX + 500, m.maxY + 500))
    }

    @Test
    fun termsMatchPlurals() {
        assertTrue("jig" in KnowledgeMap.terms("Drill jigs"))
        assertTrue("bush" in KnowledgeMap.terms("bushes") || "bushe" in KnowledgeMap.terms("bushes"))
        assertFalse("the" in KnowledgeMap.terms("the datum"))
    }
}
