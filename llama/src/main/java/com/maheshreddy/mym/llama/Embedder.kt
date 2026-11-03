package com.maheshreddy.mym.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * A GGUF sentence-embedding model (BGE, Nomic, Qwen3-Embedding, EmbeddingGemma) run by llama.cpp. [embed] returns
 * an L2-normalised vector, so the dot product of two vectors is their cosine similarity.
 */
class Embedder private constructor(private var handle: Long) : Closeable {
    private val mutex = Mutex()

    suspend fun embed(text: String): FloatArray = mutex.withLock {
        withContext(Dispatchers.Default) {
            check(handle != 0L) { "Embedder is closed" }
            LlamaNative.embed(handle, text) ?: throw LlmException("Embedding failed: ${LlamaNative.lastError()}")
        }
    }

    override fun close() {
        if (handle != 0L) {
            LlamaNative.free(handle)
            handle = 0
        }
    }

    companion object {
        /** [nCtx] tokens per text: entries longer than that are embedded from their start. */
        fun load(model: File, nCtx: Int = 512, threads: Int = 4): Embedder {
            require(model.isFile) { "Embedding model not found: $model" }
            val h = LlamaNative.loadEmbedder(model.absolutePath, nCtx, threads)
            if (h == 0L) throw LlmException("Could not load the embedding model: ${LlamaNative.lastError().ifBlank { model.name }}")
            return Embedder(h)
        }
    }
}
