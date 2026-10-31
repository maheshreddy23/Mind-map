package com.freshernotes.app.engine

import com.freshernotes.app.models.ModelKind
import com.freshernotes.app.models.ModelStore
import com.freshernotes.core.audio.EnergySpeechDetector
import com.freshernotes.core.audio.SpeechDetector
import com.freshernotes.whisper.SileroVad
import com.freshernotes.whisper.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class MissingModelException(val kind: ModelKind) : Exception("${kind.label} model is not installed")

/** Lazily loads native engines from installed models and keeps them for reuse; reloads if the file changes. */
@Singleton
class Engines @Inject constructor(private val models: ModelStore) {
    private val mutex = Mutex()
    private var whisper: Pair<File, WhisperEngine>? = null
    private var vad: Pair<File, SileroVad>? = null

    suspend fun whisper(): WhisperEngine = mutex.withLock {
        val file = models.file(ModelKind.WHISPER) ?: throw MissingModelException(ModelKind.WHISPER)
        whisper?.takeIf { it.first == file }?.let { return it.second }
        whisper?.second?.close()
        withContext(Dispatchers.IO) { WhisperEngine.load(file) }.also { whisper = file to it }
    }

    /** Silero if installed, otherwise the energy-based fallback. */
    suspend fun speechDetector(): SpeechDetector = mutex.withLock {
        val file = models.file(ModelKind.VAD) ?: return EnergySpeechDetector()
        vad?.takeIf { it.first == file }?.let { return it.second.also { d -> d.reset() } }
        vad?.second?.close()
        runCatching { withContext(Dispatchers.IO) { SileroVad.load(file) } }
            .map { it.also { v -> vad = file to v } as SpeechDetector }
            .getOrElse { EnergySpeechDetector() }
    }

    /** Frees native memory (e.g. on onTrimMemory or when the session screen closes). */
    suspend fun releaseAll() = mutex.withLock {
        whisper?.second?.close(); whisper = null
        vad?.second?.close(); vad = null
    }
}
