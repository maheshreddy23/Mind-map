package com.maheshreddy.mym.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {
    private val msgs = listOf(
        ChatMessage(Role.SYSTEM, "Be Arjun."),
        ChatMessage(Role.USER, "We use drill jigs."),
    )

    @Test
    fun chatMlRendersRolesAndAssistantPrefix() {
        val p = PromptFormat.CHATML.render(msgs, addAssistantPrefix = true)
        assertEquals(
            "<|im_start|>system\nBe Arjun.<|im_end|>\n<|im_start|>user\nWe use drill jigs.<|im_end|>\n<|im_start|>assistant\n",
            p,
        )
    }

    @Test
    fun gemmaFoldsSystemIntoFirstUserTurn() {
        val p = PromptFormat.GEMMA.render(msgs, addAssistantPrefix = true)
        assertEquals("<start_of_turn>user\nBe Arjun.\n\nWe use drill jigs.<end_of_turn>\n<start_of_turn>model\n", p)
    }

    @Test
    fun detectsFormatFromTemplate() {
        assertEquals(PromptFormat.GEMMA, PromptFormat.detect("{{ '<start_of_turn>' + role }}"))
        assertEquals(PromptFormat.LLAMA3, PromptFormat.detect("<|start_header_id|>"))
        assertEquals(PromptFormat.CHATML, PromptFormat.detect("<|im_start|>"))
        assertEquals(PromptFormat.CHATML, PromptFormat.detect(null))
        assertEquals(PromptFormat.PHI, PromptFormat.detect("{{ '<|' + message['role'] + '|>' + message['content'] + '<|end|>' }}{{ '<|assistant|>' }}"))
    }

    @Test
    fun personaMatchesSpec() {
        val p = Persona.systemPrompt()
        assertTrue(p.startsWith("You are Arjun, a fresher engineer"))
        assertTrue(p.contains("Ask ONE short, specific question at a time"))
        assertTrue(p.contains("under 40 words"))
        assertFalse(p.contains("\n"))
    }

    @Test
    fun wrapUpDetection() {
        assertTrue(Persona.isWrapUp("Okay, let's wrap up for today."))
        assertTrue(Persona.isWrapUp("Wrap it up"))
        assertTrue(Persona.isWrapUp("we're wrapping up"))
        assertFalse(Persona.isWrapUp("the part is wrapped in film"))
    }

    @Test
    fun cleanReplyKeepsFirstQuestion() {
        assertEquals("Why use a jig?", Persona.cleanReply("Arjun: Why use a jig? Also, what is NX?"))
        val long = (1..60).joinToString(" ") { "word" }
        val capped = Persona.cleanReply(long, 40)
        assertEquals(40, capped.split(" ").size)
        assertTrue(capped.endsWith("…"))
    }
}
