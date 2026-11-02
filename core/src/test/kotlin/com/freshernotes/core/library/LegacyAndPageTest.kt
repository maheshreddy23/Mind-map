package com.freshernotes.core.library

import com.freshernotes.core.notes.NoteParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyAndPageTest {
    /** Exactly what v1's SessionNote.toMarkdown() wrote into sessions.notesMarkdown. */
    private val v1Note = NoteParser.parse(
        """
        TOPIC: Drill jigs
        SUBTOPICS: bushes, datums
        ## Key concepts explained
        - Drill jigs guide the drill with hardened bushes.
        ## Procedures or steps
        1. Locate on the tooling holes.
        ## Reasons and rationale
        - none
        ## Pitfalls and lessons learned
        - Worn bushes drift the hole position.
        ## Open questions to revisit
        - none
        """.trimIndent(),
    ).toMarkdown()

    @Test
    fun splitsV1NotesIntoTypedEntries() {
        val pieces = LegacyNotes.split(v1Note, "Jigs walkthrough")
        assertEquals(listOf(EntryType.KNOWLEDGE, EntryType.LESSON), pieces.map { it.type })
        val k = pieces[0].body
        assertTrue(k, k.contains("**Sub-topics:** bushes, datums"))
        assertTrue(k.contains("- Drill jigs guide the drill with hardened bushes."))
        assertTrue(k.contains("1. Locate on the tooling holes."))
        assertFalse("empty sections are dropped", k.contains("nothing recorded"))
        assertEquals("- Worn bushes drift the hole position.", pieces[1].body)
    }

    @Test
    fun unparseableNotesAreKeptWhole() {
        val pieces = LegacyNotes.split("Some free text I typed by hand.", "Old")
        assertEquals(1, pieces.size)
        assertEquals("Some free text I typed by hand.", pieces[0].body)
        assertTrue(LegacyNotes.split("  ", "x").isEmpty())
    }

    @Test
    fun classifierIsConfidentOnlyWithClearEvidence() {
        val jig = SubjectClassifier.guess("Drill jigs", "jig bushes locate on datum holes; the fixture clamps the skin")!!
        assertEquals("Jigs and Fixtures", jig.subject)
        assertTrue(jig.confident)

        val plm = SubjectClassifier.guess("Teamcenter release", "ECO workflow and BOM revision")!!
        assertEquals("PLM", plm.subject)
        assertTrue(plm.confident)

        // CAD is not a spec-named subject, so it is only ever a proposal.
        val cad = SubjectClassifier.guess("CATIA sketching", "catia sketch constraint part design surface")!!
        assertEquals("CAD", cad.subject)
        assertFalse(cad.confident)

        // Mixed evidence is not confident.
        val mixed = SubjectClassifier.guess("Jig release", "jig fixture datum teamcenter release workflow")!!
        assertFalse(mixed.confident)

        assertNull(SubjectClassifier.guess("Cooking", "I made dal today"))
    }

    @Test
    fun pageGroupsNumbersAndDeduplicates() {
        val page = PageBuilder.build(
            "Drill jigs", "Jigs and Fixtures", "",
            listOf(
                PageEntry(1, "Bushes", EntryType.KNOWLEDGE, "- Bushes are hardened.\n- They guide the drill.", 1),
                PageEntry(2, "Worn bushes", EntryType.LESSON, "- Replace worn bushes.", 3),
                PageEntry(3, "Repeat", EntryType.KNOWLEDGE, "- bushes are hardened!\n## Detail\n- New point.", 2),
                PageEntry(4, "Dupe only", EntryType.IDEA, "- They guide the drill.", 4),
            ),
        )
        assertTrue(page, page.startsWith("# Drill jigs\n\n_Jigs and Fixtures_\n\n## Knowledge"))
        assertTrue(page.contains("### 1. Bushes"))
        assertTrue(page.contains("### 2. Repeat\n**Detail**\n- New point."))
        assertEquals("duplicate bullet removed", 1, Regex("(?i)bushes are hardened").findAll(page).count())
        assertTrue(page.contains("## Lessons\n\n### 3. Worn bushes"))
        assertTrue(page.contains("### 4. Dupe only\n_(covered above)_"))
        assertTrue(page.indexOf("## Knowledge") < page.indexOf("## Lessons"))
    }

    @Test
    fun emptyPage() = assertTrue(PageBuilder.build("T", null, "", emptyList()).contains("_No entries yet._"))
}

class LegacyPlannerTest {
    private fun note(concept: String, pitfall: String? = null) =
        "# x\n\n## Key concepts explained\n- $concept\n\n## Pitfalls and lessons learned\n- ${pitfall ?: "_(nothing recorded)_"}\n"

    @Test
    fun confidentTopicsAreFiledUncertainOnesGoToInbox() {
        val topics = listOf(
            LegacyTopic(1, "Drill jigs", "# Drill jigs\n- jig fixture datum bush"),
            LegacyTopic(2, "Weekend thoughts", "# Weekend thoughts\n- stuff"),
            LegacyTopic(3, "Hand made page", "## Key concepts explained\n- Locator pins wear.\n"),
        )
        val sessions = listOf(
            LegacySession(10, "Jigs 1", 100, 1, note("Jig bushes guide the drill.", "Worn bushes drift.")),
            LegacySession(11, "Thoughts", 200, 2, note("Rest matters.")),
            LegacySession(12, "Lost", 300, 99, note("Orphan note.")),
            LegacySession(13, "No notes", 400, null, ""),
        )
        val plan = LegacyPlanner.plan(topics, sessions)
        assertEquals(listOf("Jigs and Fixtures"), plan.subjects)
        assertEquals(mapOf(1L to "Jigs and Fixtures"), plan.topicSubjects)

        val jig = plan.entries.filter { it.sessionId == 10L }
        assertEquals(listOf(EntryType.KNOWLEDGE, EntryType.LESSON), jig.map { it.type })
        assertTrue(jig.all { it.status == EntryStatus.FILED && it.topicId == 1L && it.createdAt == 100L })

        val thoughts = plan.entries.single { it.sessionId == 11L }
        assertEquals(EntryStatus.INBOX, thoughts.status)
        assertEquals(2L, thoughts.topicId)

        val orphan = plan.entries.single { it.sessionId == 12L }
        assertEquals(EntryStatus.INBOX, orphan.status)
        assertNull(orphan.topicId)

        val handMade = plan.entries.single { it.sessionId == null }
        assertEquals(3L, handMade.topicId)
        assertTrue(handMade.body.contains("Locator pins wear."))

        assertTrue("sessions without notes produce nothing", plan.entries.none { it.sessionId == 13L })
    }

    @Test
    fun v1GeneralTopicGoesToTheInboxEvenWhenTheGuessIsConfident() {
        val topics = listOf(LegacyTopic(1, "General", "# General\n- jig fixture datum bush clamp drill"))
        val sessions = listOf(LegacySession(10, "s", 100, 1, note("Jig bushes guide the drill.", "Worn bushes drift.")))
        val plan = LegacyPlanner.plan(topics, sessions)
        assertTrue(plan.topicSubjects.isEmpty())
        assertTrue(plan.entries.all { it.status == EntryStatus.INBOX && it.topicId == null && it.proposedSubject == "Jigs and Fixtures" })
        assertTrue(plan.entries.first().reason.contains("catch-all"))
    }
}
