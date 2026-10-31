package com.freshernotes.whisper

import com.freshernotes.core.audio.SpeechDetector
import java.io.Closeable
import java.io.File

/** Silero VAD (ggml-silero-*.bin) running inside whisper.cpp, used as a streaming [SpeechDetector]. */
class SileroVad private constructor(private var ctx: Long) : SpeechDetector, Closeable {
    @Synchronized
    override fun speechProbability(frame: FloatArray): Float {
        check(ctx != 0L) { "SileroVad is closed" }
        val p = WhisperNative.vadProbability(ctx, frame)
        check(p >= 0f) { "VAD inference failed" }
        return p
    }

    @Synchronized
    override fun reset() {
        if (ctx != 0L) WhisperNative.vadReset(ctx)
    }

    @Synchronized
    override fun close() {
        if (ctx != 0L) {
            WhisperNative.vadFree(ctx)
            ctx = 0L
        }
    }

    companion object {
        fun load(model: File, threads: Int = 1): SileroVad {
            require(model.isFile) { "VAD model not found: $model" }
            val ctx = WhisperNative.vadInit(model.absolutePath, threads)
            check(ctx != 0L) { "Failed to load VAD model ${model.name}" }
            return SileroVad(ctx)
        }
    }
}
