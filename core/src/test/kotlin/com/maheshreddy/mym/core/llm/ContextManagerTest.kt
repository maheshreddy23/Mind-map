package com.maheshreddy.mym.core.llm

import com.maheshreddy.mym.core.ChatTurn
import com.maheshreddy.mym.core.Speaker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {
    private val system = Persona.systemPrompt()
    private fun turns(n: Int, words: Int = 20) = (0 until n).map {
        ChatTurn(if (it % 2 == 0) Speaker.SENIOR else Speaker.FRESHER, (0 until words).joinToString(" ") { w -> "w$it-$w" })
    }

    @Test
    fun mapsSpeakersToRolesAndIncludesSummary() {
        val cm = ContextManager(FakeModel())
        val msgs = cm.messages(ConversationMemory("- jigs locate parts", 2, turns(4)), system)
        assertEquals(listOf(Role.SYSTEM, Role.USER, Role.ASSISTANT), msgs.map { it.role })
        assertTrue(msgs[0].content.contains("- jigs locate parts"))
    }

    @Test
    fun mergesConsecutiveSeniorTurns() {
        val t = listOf(ChatTurn(Speaker.SENIOR, "a"), ChatTurn(Speaker.SENIOR, "b"), ChatTurn(Speaker.FRESHER, "q?"))
        val msgs = ContextManager(FakeModel()).messages(ConversationMemory("", 0, t), system)
        assertEquals("a\nb", msgs[1].content)
        assertEquals(3, msgs.size)
    }

    @Test
    fun nudgeIsAppendedToLastSeniorTurnOnly() {
        val t = listOf(ChatTurn(Speaker.SENIOR, "a"), ChatTurn(Speaker.FRESHER, "q?"), ChatTurn(Speaker.SENIOR, "b"))
        val msgs = ContextManager(FakeModel()).messages(ConversationMemory("", 0, t), system, nudge = Persona.nudge(40))
        assertEquals("a", msgs[1].content)
        assertTrue(msgs[3].content.startsWith("b\n\n(Reply as Arjun"))
    }

    @Test
    fun fallbackQuestionsAreQuestions() {
        (0 until 10).forEach { assertTrue(Persona.isQuestion(Persona.fallbackQuestion(it))) }
        assertFalse(Persona.isQuestion("1. Drill jigs are used…"))
    }

    @Test
    fun compactsOnlyAboveSeventyPercent() {
        val model = FakeModel(contextSize = 1000)
        val cm = ContextManager(model)
        assertFalse(cm.needsCompaction(ConversationMemory("", 0, turns(10)), system)) // ~300 tokens
        assertTrue(cm.needsCompaction(ConversationMemory("", 0, turns(30)), system)) // ~800 tokens
    }

    @Test
    fun compactionFoldsOldTurnsAndKeepsRecentOnes() = runTest {
        val model = FakeModel(contextSize = 1000) { "- summary of the old part" }
        val cm = ContextManager(model, keepRecentTurns = 6)
        val before = ConversationMemory("", 0, turns(30))
        val after = cm.compact(before)
        assertEquals("- summary of the old part", after.summary)
        assertEquals(24, after.summarizedCount)
        assertEquals(6, after.recentTurns.size)
        assertFalse(cm.needsCompaction(after, system))
        // The summarizer saw the old turns, not the kept ones.
        val prompt = model.calls.single().last().content
        assertTrue(prompt.contains("w0-0") && !prompt.contains("w29-0"))
    }

    @Test
    fun compactionIncludesPreviousSummary() = runTest {
        val model = FakeModel { "- merged" }
        ContextManager(model).compact(ConversationMemory("- older notes", 4, turns(20)))
        assertTrue(model.calls.single().last().content.contains("Current notes:\n- older notes"))
    }

    @Test
    fun questionPolicy() {
        val s = { t: String -> ChatTurn(Speaker.SENIOR, t) }
        val f = { t: String -> ChatTurn(Speaker.FRESHER, t) }
        assertEquals(QuestionPolicy.Decision.Ask, QuestionPolicy.decide(listOf(s("jigs hold parts")), 1))
        assertEquals(QuestionPolicy.Decision.Listen, QuestionPolicy.decide(listOf(s("a"), f("q?"), s("b")), 2))
        assertEquals(QuestionPolicy.Decision.Ask, QuestionPolicy.decide(listOf(s("a"), f("q?"), s("b"), s("c")), 2))
        assertEquals(QuestionPolicy.Decision.WrapUp, QuestionPolicy.decide(listOf(s("ok let's wrap up")), 1))
        assertEquals(QuestionPolicy.Decision.Listen, QuestionPolicy.decide(emptyList(), 1))
    }
}
