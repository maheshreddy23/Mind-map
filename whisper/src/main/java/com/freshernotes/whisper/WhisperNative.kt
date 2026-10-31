package com.freshernotes.whisper

/** Raw JNI surface of libwhisper_jni.so. Prefer [WhisperEngine]. */
internal object WhisperNative {
    init {
        System.loadLibrary("whisper_jni")
        installLogger()
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(ctx: Long)
    external fun requestAbort(ctx: Long)
    external fun transcribe(ctx: Long, samples: FloatArray, threads: Int, prompt: String?): String?
    external fun systemInfo(): String

    external fun vadInit(modelPath: String, threads: Int): Long
    external fun vadProbability(ctx: Long, samples: FloatArray): Float
    external fun vadReset(ctx: Long)
    external fun vadFree(ctx: Long)
    external fun installLogger()
}
