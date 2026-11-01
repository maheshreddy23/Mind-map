package com.freshernotes.core.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    private val llmOutput = """
        TOPIC: drill jigs
        SUBTOPICS: tooling holes, bushes; datums
        ## Key concepts explained
        - A drill jig positions holes using hardened bushes.
        * Tooling holes act as datums.
        ## Procedures or steps
        1. Locate the jig on tooling holes.
        2. Clamp, then drill through the bushes.
        **Reasons and rationale**
        - Repeatable hole positions on every aircraft.
        ## Pitfalls and lessons learned
        - none
        ## Open Questions
        - How often are bushes replaced?
    """.trimIndent()

    @Test
    fun parsesTopicSubtopicsAndSections() {
        val note = NoteParser.parse(llmOutput)
        assertEquals("Drill jigs", note.topic)
        assertEquals(listOf("tooling holes", "bushes", "datums"), note.subtopics)
        assertEquals(2, note.sections[Section.CONCEPTS]!!.size)
        assertEquals("- Tooling holes act as datums.", note.sections[Section.CONCEPTS]!![1])
        assertEquals("1. Locate the jig on tooling holes.", note.sections[Section.PROCEDURES]!![0])
        assertEquals(1, note.sections[Section.RATIONALE]!!.size)
        assertNull("'none' is dropped", note.sections[Section.PITFALLS])
        assertEquals(1, note.sections[Section.OPEN_QUESTIONS]!!.size)
    }

    @Test
    fun fallsBackWhenTopicMissingWithoutACatchAll() = assertEquals("Untitled session", NoteParser.parse("## Key concepts\n- x").topic)

    @Test
    fun markdownHasAllRequiredSections() {
        val md = NoteParser.parse(llmOutput).toMarkdown()
        Section.entries.forEach { assertTrue("missing ${it.heading}", md.contains("## ${it.heading}")) }
        assertTrue(md.startsWith("# Drill jigs"))
    }

    @Test
    fun matchesSimilarTopics() {
        val existing = listOf("Drill Jigs and Fixtures", "Teamcenter workflows", "CATIA surfacing")
        assertEquals("Drill Jigs and Fixtures", TopicMatcher.bestMatch("drill jig", existing))
        assertEquals("Teamcenter workflows", TopicMatcher.bestMatch("Teamcenter workflow", existing))
        assertNull(TopicMatcher.bestMatch("NX sheet metal", existing))
    }

    @Test
    fun mergeAddsNewBulletsOnceAndKeepsUserSections() {
        val first = TopicPageMerger.merge("", "Drill jigs", NoteParser.parse(llmOutput), "2026-10-31 Session A")
        val userEdited = first + "\n## My own notes\n- check with Ravi\n"
        val second = NoteParser.parse(
            """
            TOPIC: Drill jigs
            SUBTOPICS: Bush wear
            ## Key concepts explained
            - a drill jig positions holes using hardened bushes
            - Bushes wear and must be gauged.
            ## Pitfalls and lessons learned
            - Never drill without clamping the jig.
            """.trimIndent(),
        )
        val merged = TopicPageMerger.merge(userEdited, "Drill jigs", second, "2026-11-02 Session B")
        assertEquals(1, Regex("hardened bushes", RegexOption.IGNORE_CASE).findAll(merged).count())
        assertTrue(merged.contains("- Bushes wear and must be gauged."))
        assertTrue(merged.contains("- Never drill without clamping the jig."))
        assertTrue(!merged.contains("## Pitfalls and lessons learned\n- _(nothing recorded)_"))
        assertTrue(merged.contains("## My own notes\n- check with Ravi"))
        assertTrue(merged.contains("**Sub-topics:** tooling holes, bushes, datums, Bush wear"))
        assertTrue(merged.trimEnd().endsWith("- 2026-10-31 Session A\n- 2026-11-02 Session B"))
        // Merging is idempotent for the same note.
        assertEquals(merged, TopicPageMerger.merge(merged, "Drill jigs", second, "2026-11-02 Session B"))
    }
}
