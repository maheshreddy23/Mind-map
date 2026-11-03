package com.maheshreddy.mym.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Thread-safe wrapper around one whisper.cpp context. Input is 16 kHz mono float PCM in [-1, 1].
 */
class WhisperEngine private constructor(private var ctx: Long) : Closeable {
    private val mutex = Mutex()

    /**
     * Transcribes [samples]. [prompt] biases the decoder towards domain vocabulary
     * (for example "CATIA, NX, Teamcenter, PLM, jig, fixture").
     */
    /** [language] is "en" or "auto" (multilingual models only; English-only models always use English). */
    suspend fun transcribe(samples: FloatArray, prompt: String? = null, threads: Int = defaultThreads(), language: String = "en"): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                check(ctx != 0L) { "WhisperEngine is closed" }
                WhisperNative.transcribe(ctx, samples, threads, prompt, language)?.trim()
                    ?: throw IllegalStateException("whisper transcription failed")
            }
        }

    /** Stops an in-progress [transcribe] early; it then returns whatever was decoded so far. */
    fun abort() {
        if (ctx != 0L) WhisperNative.requestAbort(ctx)
    }

    override fun close() {
        if (ctx != 0L) {
            WhisperNative.freeContext(ctx)
            ctx = 0L
        }
    }

    companion object {
        /** Loads a ggml whisper model (e.g. ggml-base.en.bin). Blocking; call off the main thread. */
        fun load(model: File): WhisperEngine {
            require(model.isFile) { "Whisper model not found: $model" }
            val ctx = WhisperNative.initContext(model.absolutePath)
            check(ctx != 0L) {
                val reason = WhisperNative.lastError().ifBlank { "no details from whisper.cpp" }
                "Failed to load whisper model ${model.name} (${model.length() / (1 shl 20)} MB): $reason"
            }
            return WhisperEngine(ctx)
        }

        fun systemInfo(): String = WhisperNative.systemInfo()

        /** Big cores only: the 8 Gen 3 has 1+5 performance cores; more threads than that just contend. */
        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8).let { (it - 2).coerceAtLeast(1) }.coerceAtMost(6)
    }
}
