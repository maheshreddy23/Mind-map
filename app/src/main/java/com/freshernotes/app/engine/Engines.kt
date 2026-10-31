package com.freshernotes.app.engine

import android.content.Context
import com.freshernotes.app.models.ModelKind
import com.freshernotes.tts.PiperSynthesizer
import com.freshernotes.tts.SpeechSynthesizer
import com.freshernotes.tts.SystemSynthesizer
import dagger.hilt.android.qualifiers.ApplicationContext
import com.freshernotes.app.models.ModelStore
import com.freshernotes.core.audio.EnergySpeechDetector
import com.freshernotes.core.audio.SpeechDetector
import com.freshernotes.llama.LlamaEngine
import com.freshernotes.whisper.SileroVad
import com.freshernotes.whisper.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the voice used for the fresher's questions. */
fun interface SynthesizerProvider {
    suspend fun synthesizer(preferPiper: Boolean): SpeechSynthesizer
}

class MissingModelException(val kind: ModelKind) : Exception("${kind.label} model is not installed")

/** Lazily loads native engines from installed models and keeps them for reuse; reloads if the file changes. */
@Singleton
class Engines @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelStore,
) : SynthesizerProvider {
    private val mutex = Mutex()
    private var whisper: Pair<File, WhisperEngine>? = null
    private var vad: Pair<File, SileroVad>? = null
    private var llm: Pair<File, LlamaEngine>? = null
    private var piper: Pair<File, PiperSynthesizer>? = null
    private var system: SystemSynthesizer? = null

    /** Piper when chosen and installed; otherwise (or if Piper fails to load) the Android TTS fallback. */
    override suspend fun synthesizer(preferPiper: Boolean): SpeechSynthesizer = mutex.withLock {
        val voice = models.file(ModelKind.TTS)
        if (preferPiper && voice != null) {
            piper?.takeIf { it.first == voice }?.let { return it.second }
            piper?.second?.close(); piper = null
            val loaded = runCatching { withContext(Dispatchers.IO) { PiperSynthesizer.load(voice) } }.getOrNull()
            if (loaded != null) {
                piper = voice to loaded
                return loaded
            }
        }
        system ?: SystemSynthesizer(context).also { system = it }
    }

    suspend fun llm(): LlamaEngine = mutex.withLock {
        val file = models.file(ModelKind.LLM) ?: throw MissingModelException(ModelKind.LLM)
        llm?.takeIf { it.first == file }?.let { return it.second }
        llm?.second?.close()
        withContext(Dispatchers.IO) { LlamaEngine.load(file, nCtx = LLM_CONTEXT) }.also { llm = file to it }
    }

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
        llm?.second?.close(); llm = null
        piper?.second?.close(); piper = null
        system?.close(); system = null
    }

    companion object {
        const val LLM_CONTEXT = 4096
    }
}
