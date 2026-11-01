package com.freshernotes.llama

/** Receives UTF-8 chunks as they are generated; return false to stop. */
fun interface TokenCallback {
    fun onToken(utf8: ByteArray): Boolean
}

internal object LlamaNative {
    init {
        System.loadLibrary("llama_jni")
        backendInit()
    }

    external fun backendInit()
    external fun load(modelPath: String, nCtx: Int, threads: Int): Long
    external fun free(handle: Long)
    external fun contextSize(handle: Long): Int
    external fun countTokens(handle: Long, text: String): Int
    external fun chatTemplate(handle: Long): String?
    external fun setThreads(handle: Long, threads: Int)
    external fun requestAbort(handle: Long)
    external fun clearCache(handle: Long)
    external fun generate(
        handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float, seed: Int, callback: TokenCallback,
    ): Int
    external fun systemInfo(): String
    external fun lastError(): String
}
