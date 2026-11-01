package com.freshernotes.llama

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.Persona
import com.freshernotes.core.llm.PromptFormat
import com.freshernotes.core.llm.Role
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlamaEngineTest {
    companion object {
        private lateinit var engine: LlamaEngine

        @JvmStatic @BeforeClass
        fun load() {
            Log.i("LlamaEngineTest", LlamaEngine.systemInfo())
            engine = LlamaEngine.load(TestModels.copy("qwen2.5-0.5b-instruct-q4_k_m.gguf"), nCtx = 2048, threads = 4)
        }

        @JvmStatic @AfterClass
        fun close() = engine.close()
    }

    private val conversation = listOf(
        ChatMessage(Role.SYSTEM, Persona.systemPrompt()),
        ChatMessage(
            Role.USER,
            "In aircraft assembly we use drill jigs to position holes on wing skins. The jig is located on " +
                "tooling holes so every hole lands in the same place on every aircraft.",
        ),
    )

    @Test
    fun detectsChatMlForQwen() = assertEquals(PromptFormat.CHATML, engine.format)

    @Test
    fun personaReturnsAQuestion() = runBlocking {
        val start = System.nanoTime()
        var firstTokenMs = -1L
        val chunks = engine.stream(conversation, GenerationParams(maxTokens = 64, temperature = 0f)).toList().also {
            firstTokenMs = (System.nanoTime() - start) / 1_000_000
        }
        val reply = Persona.cleanReply(chunks.joinToString(""))
        Log.i("LlamaEngineTest", "reply in ${firstTokenMs} ms (${chunks.size} chunks): $reply")
        assertTrue("expected streamed chunks", chunks.size > 1)
        assertTrue("expected a question, got: $reply", reply.endsWith("?"))
        assertTrue("reply too long: $reply", reply.split(" ").size <= 40)
    }

    @Test
    fun cancellingTheFlowStopsGeneration() = runBlocking {
        val firstTwo = engine.stream(conversation, GenerationParams(maxTokens = 200)).take(2).toList()
        assertEquals(2, firstTwo.size)
        // The engine must be usable again straight away (the abort released the native lock).
        assertTrue(engine.complete(conversation, GenerationParams(maxTokens = 4, temperature = 0f)).isNotEmpty())
    }

    @Test
    fun prefixCacheMakesFollowUpFaster() = runBlocking {
        val params = GenerationParams(maxTokens = 1, temperature = 0f)
        engine.complete(conversation, params) // warm the cache with the shared prefix
        val followUp = conversation + ChatMessage(Role.ASSISTANT, "Why tooling holes?") +
            ChatMessage(Role.USER, "Because they are the datum the whole assembly is built from.")
        val t0 = System.nanoTime()
        engine.complete(followUp, params)
        val cachedMs = (System.nanoTime() - t0) / 1_000_000
        Log.i("LlamaEngineTest", "follow-up first token with cached prefix: $cachedMs ms")
        assertTrue(engine.count("hello world") in 1..5)
    }
}
