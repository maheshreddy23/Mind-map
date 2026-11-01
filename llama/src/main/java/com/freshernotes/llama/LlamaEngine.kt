package com.freshernotes.llama

import com.freshernotes.core.llm.ChatMessage
import com.freshernotes.core.llm.PromptFormat
import com.freshernotes.core.llm.TokenCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class GenerationParams(
    val maxTokens: Int = 96,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val seed: Int = 42,
)

class LlmException(message: String) : Exception(message)

/**
 * One loaded GGUF model and context. Calls are serialized; the native side keeps the KV cache between
 * calls so that a prompt sharing a prefix with the previous one (same persona, older turns) is fast.
 */
class LlamaEngine private constructor(private var handle: Long) : Closeable, TokenCounter {
    private val mutex = Mutex()

    val contextSize: Int = LlamaNative.contextSize(handle)

    /** Picks ChatML, Gemma or Llama 3 formatting from the model's embedded chat template. */
    val format: PromptFormat = PromptFormat.detect(LlamaNative.chatTemplate(handle))

    override fun count(text: String): Int = synchronized(this) {
        check(handle != 0L) { "engine closed" }
        LlamaNative.countTokens(handle, text)
    }

    /** Streams the reply to [messages] chunk by chunk. Cancelling the flow aborts generation. */
    fun stream(messages: List<ChatMessage>, params: GenerationParams = GenerationParams()): Flow<String> =
        streamRaw(format.render(messages, addAssistantPrefix = true), params)

    fun streamRaw(prompt: String, params: GenerationParams = GenerationParams()): Flow<String> = callbackFlow {
        val job = launch(Dispatchers.Default) {
            mutex.withLock {
                check(handle != 0L) { "engine closed" }
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                val result = LlamaNative.generate(
                    handle, prompt, params.maxTokens, params.temperature, params.topP, params.seed,
                ) { bytes ->
                    val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
                    trySend(text).isSuccess && !isClosedForSend
                }
                when (result) {
                    -2 -> close(LlmException("Prompt does not fit the context window"))
                    -1 -> close(LlmException("Generation failed"))
                    else -> close()
                }
            }
        }
        awaitClose {
            if (job.isActive) LlamaNative.requestAbort(handle)
        }
    }.flowOn(Dispatchers.Default)

    /** Convenience: full reply as a string. */
    suspend fun complete(messages: List<ChatMessage>, params: GenerationParams = GenerationParams()): String {
        val sb = StringBuilder()
        stream(messages, params).collect { sb.append(it) }
        return sb.toString().trim()
    }

    /** Lowers or raises CPU threads at runtime (thermal back-off). */
    fun setThreads(threads: Int) {
        if (handle != 0L) LlamaNative.setThreads(handle, threads.coerceAtLeast(1))
    }

    fun abort() {
        if (handle != 0L) LlamaNative.requestAbort(handle)
    }

    override fun close() {
        synchronized(this) {
            if (handle != 0L) {
                LlamaNative.free(handle)
                handle = 0L
            }
        }
    }

    companion object {
        /** Blocking; call off the main thread. 4096 tokens of context fits a 3B Q4 model in ~3 GB. */
        fun load(model: File, nCtx: Int = 4096, threads: Int = defaultThreads()): LlamaEngine {
            if (!model.isFile) throw LlmException("LLM model not found: $model")
            val handle = LlamaNative.load(model.absolutePath, nCtx, threads)
            if (handle == 0L) {
                val reason = LlamaNative.lastError().ifBlank { "no details from llama.cpp" }
                throw LlmException("Failed to load ${model.name} (${model.length() / (1 shl 20)} MB): $reason")
            }
            return LlamaEngine(handle)
        }

        fun systemInfo(): String = LlamaNative.systemInfo()

        /** Performance cores only; token generation is memory-bound so more threads do not help. */
        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 6)
    }
}
