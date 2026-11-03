package com.maheshreddy.mym.core.library

import com.maheshreddy.mym.core.llm.ButlerProfile
import com.maheshreddy.mym.core.llm.ButlerPrompt
import com.maheshreddy.mym.core.llm.InterviewStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAndButlerTest {
    private val sources = listOf(
        AskSource(11, "Won bid", "PLM gets a project number.", "RFP to kickoff"),
        AskSource(12, "Kickoff", "The kickoff assigns teams; CCPM allocates tasks.", "RFP to kickoff"),
    )

    @Test
    fun keywordsDropQuestionWords() {
        assertEquals(listOf("datum", "selection"), AskPrompt.keywords("What have I said about datum selection?"))
        assertEquals(listOf("happens", "win", "bid"), AskPrompt.keywords("What happens after we win a bid?"))
        assertTrue(AskPrompt.keywords("What have I said?").isEmpty())
    }

    @Test
    fun parsesCitationsAndDropsUnknownOnes() {
        val parts = AskPrompt.parse("You create the project number in PLM [1], then teams are assigned [2][7]. Also [1, 2].", sources)
        val cites = parts.filterIsInstance<AnswerPart.Cite>().map { it.number }
        assertEquals(listOf(1, 2, 1, 2), cites)
        assertEquals(listOf(11L, 12L), AskPrompt.cited(parts))
        val text = parts.filterIsInstance<AnswerPart.Text>().joinToString("") { it.text }
        assertFalse(text.contains("["))
        assertTrue(text.contains("teams are assigned."))
    }

    @Test
    fun recognisesNothing() {
        assertTrue(AskPrompt.isNothing("NOTHING"))
        assertTrue(AskPrompt.isNothing(" nothing. "))
        assertFalse(AskPrompt.isNothing("Nothing about datums, but [1] covers bids."))
    }

    @Test
    fun promptNumbersSourcesAndAsksForCitations() {
        val m = AskPrompt.messages("M", "What happens after we win a bid?", sources)
        assertTrue(m[0].content.contains("ONLY the numbered entries"))
        assertTrue(m[1].content.contains("[2] Kickoff (RFP to kickoff): The kickoff"))
    }

    @Test
    fun butlerPromptFollowsTheProfileAndStaysStable() {
        val p = ButlerProfile(name = "Jeeva", callsYou = "Mahesh", casual = 0.9f, witty = 0.1f, style = InterviewStyle.SCEPTICAL_REVIEWER)
        val s = ButlerPrompt.system(p, 40)
        assertTrue(s.startsWith("You are Jeeva,"))
        assertTrue(s.contains("Address the user as \"Mahesh\""))
        assertTrue(s.contains("relaxed and casual"))
        assertTrue(s.contains("sceptical reviewer"))
        assertTrue(s.contains("under 40 words"))
        assertEquals("same profile, same prompt (cacheable prefix)", s, ButlerPrompt.system(p.copy(), 40))
        val d = ButlerPrompt.system(ButlerProfile(), 40)
        assertTrue(d.startsWith("You are M,"))
        assertTrue(d.contains("M.Tech-level fundamentals"))
        assertFalse("never names a film character", d.contains("Jarvis", ignoreCase = true))
        assertTrue(ButlerPrompt.nudge(p, 40).contains("Reply as Jeeva"))
    }
}
