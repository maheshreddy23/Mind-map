package com.freshernotes.core.library

import com.freshernotes.core.ChatTurn
import com.freshernotes.core.Speaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilingTest {
    private val library = listOf(
        SubjectRef(1, "PLM", listOf(TopicRef(10, "Engineering change orders"), TopicRef(11, "Teamcenter workflows"))),
        SubjectRef(2, "Jigs and Fixtures", listOf(TopicRef(20, "Drill jigs"))),
    )

    private val twoEntries = """
        Sure, here are the entries.
        === ENTRY
        SUBJECT: PLM
        TOPIC: Engineering change orders
        TYPE: Lesson
        CONFIDENCE: high
        PRIVACY: Shareable
        TITLE: Always attach the reason code
        - An ECO without a reason code gets rejected at CCB.
        * Reviewers need the why, not just the what.

        === ENTRY
        SUBJECT: Life
        TOPIC: Morning routine
        TYPE: reflection
        CONFIDENCE: low
        TITLE: Walk before work
        - Walking clears my head.
    """.trimIndent()

    @Test
    fun parsesEntriesAndNormalizesBullets() {
        val entries = FilingParser.parse(twoEntries)
        assertEquals(2, entries.size)
        val e = entries[0]
        assertEquals("PLM", e.subject)
        assertEquals(EntryType.LESSON, e.type)
        assertTrue(e.modelSaysHigh)
        assertEquals(Privacy.SHAREABLE, e.privacy)
        assertEquals("- An ECO without a reason code gets rejected at CCB.\n- Reviewers need the why, not just the what.", e.body)
        assertEquals(EntryType.REFLECTION, entries[1].type)
        assertFalse(entries[1].modelSaysHigh)
    }

    @Test
    fun toleratesMissingMarkersAndTitles() {
        val raw = "SUBJECT: PLM\nTOPIC: BOM\nTYPE: Knowledge\n- EBOM and MBOM differ.\nSUBJECT: PLM\nTOPIC: BOM\n- Second idea."
        val entries = FilingParser.parse(raw)
        assertEquals(2, entries.size)
        assertEquals("EBOM and MBOM differ", entries[0].title)
        assertEquals(EntryType.KNOWLEDGE, entries[0].type)
        assertNull(entries[1].type)
    }

    @Test
    fun garbageGivesNoEntries() {
        assertTrue(FilingParser.parse("I could not do that.").isEmpty())
        assertTrue(FilingParser.parse("").isEmpty())
    }

    @Test
    fun autoFilesOnlyConfidentMatchesToExistingTopics() {
        val (eco, life) = FilingParser.parse(twoEntries)
        val d = FilingPolicy.decide(eco, library)
        assertEquals(EntryStatus.FILED, d.status)
        assertEquals(1L, d.subjectId)
        assertEquals(10L, d.topicId)
        assertTrue(d.confidence >= FilingPolicy.AUTO_FILE)

        val l = FilingPolicy.decide(life, library)
        assertEquals(EntryStatus.INBOX, l.status)
        assertNull(l.subjectId)
        assertEquals("Life", l.proposedSubject)
        assertEquals("Morning routine", l.proposedTopic)
        assertTrue(l.reason, l.reason.contains("new subject", ignoreCase = true))
    }

    @Test
    fun highConfidenceStillNeedsConfirmationForNewTopic() {
        val p = EntryProposal("PLM", "Release statuses", EntryType.KNOWLEDGE, "t", "- b", modelSaysHigh = true, privacy = null)
        val d = FilingPolicy.decide(p, library)
        assertEquals(EntryStatus.INBOX, d.status)
        assertEquals(1L, d.subjectId)
        assertNull(d.topicId)
        assertTrue(d.reason.contains("new topic", ignoreCase = true))
    }

    @Test
    fun similarNamesMatchExistingRecords() {
        val p = EntryProposal("plm", "Engineering change order", EntryType.KNOWLEDGE, "t", "- b", true, null)
        assertEquals(10L, FilingPolicy.decide(p, library).topicId)
    }

    @Test
    fun topicUnderAnotherSubjectGoesToInboxPrefilled() {
        val p = EntryProposal("PLM", "Drill jigs", EntryType.KNOWLEDGE, "t", "- b", true, null)
        val d = FilingPolicy.decide(p, library)
        assertEquals(EntryStatus.INBOX, d.status)
        assertEquals(20L, d.topicId)
        assertEquals(2L, d.subjectId)
    }

    @Test
    fun catchAllNamesAreNeverUsed() {
        for (name in listOf("General", "misc", "Other", " Uncategorized ", "", null)) {
            val p = EntryProposal(name, name, EntryType.IDEA, "t", "- b", true, null)
            val d = FilingPolicy.decide(p, library)
            assertEquals(EntryStatus.INBOX, d.status)
            assertNull("$name", d.proposedSubject)
            assertNull("$name", d.proposedTopic)
        }
        assertFalse(CatchAll.isCatchAll("General Electric engines"))
    }

    @Test
    fun modelCanOnlyTightenPrivacy() {
        val shareable = EntryProposal("PLM", "Engineering change orders", EntryType.KNOWLEDGE, "t", "- b", true, Privacy.SHAREABLE)
        assertEquals(Privacy.PRIVATE, FilingPolicy.decide(shareable, library).privacy)
        assertEquals(Privacy.PERSONAL, FilingPolicy.decide(shareable, library, Privacy.PERSONAL).privacy)
        val private = shareable.copy(privacy = Privacy.PRIVATE)
        assertEquals(Privacy.PRIVATE, FilingPolicy.decide(private, library, Privacy.SHAREABLE).privacy)
        assertEquals(Privacy.PRIVATE, Privacy.DEFAULT)
    }

    @Test
    fun promptListsLibraryAndForbidsCatchAll() {
        val msgs = FilingPrompts.forSession(library, "", listOf(ChatTurn(Speaker.SENIOR, "ECOs need reasons.")))
        assertTrue(msgs[0].content.contains("Never use a catch-all"))
        assertTrue(msgs[1].content.contains("- PLM: Engineering change orders; Teamcenter workflows"))
        assertTrue(FilingPrompts.library(emptyList()).contains("empty"))
    }

    @Test
    fun entryTypeParsing() {
        assertEquals(EntryType.OPEN_QUESTION, EntryType.parse("Open question"))
        assertEquals(EntryType.OPEN_QUESTION, EntryType.parse("open_question"))
        assertEquals(EntryType.PRINCIPLE, EntryType.parse("rule of thumb"))
        assertEquals(EntryType.LESSON, EntryType.parse("**Lesson**"))
        assertNull(EntryType.parse("banana"))
    }
}
