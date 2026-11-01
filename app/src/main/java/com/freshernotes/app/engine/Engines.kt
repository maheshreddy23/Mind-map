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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the voice used for the fresher's questions. */
fun interface SynthesizerProvider {
    suspend fun synthesizer(preferPiper: Boolean): SpeechSynthesizer
}

class MissingModelException(val kind: ModelKind) : Exception("${kind.label} model is not installed")

enum class LoadState { NOT_LOADED, LOADING, READY, FAILED }

/** Load status of the two heavy engines, for "Loading speech model…" style indicators. */
data class EngineStatus(
    val whisper: LoadState = LoadState.NOT_LOADED,
    val llm: LoadState = LoadState.NOT_LOADED,
    val error: String? = null,
)

/**
 * Lazily loads native engines from installed models and keeps them for reuse; reloads if the file changes.
 * Each engine has its own lock, so loading the 2 GB LLM never delays whisper (or vice versa).
 */
@Singleton
class Engines @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelStore,
) : SynthesizerProvider {
    private val whisperLock = Mutex()
    private val llmLock = Mutex()
    private val vadLock = Mutex()
    private val ttsLock = Mutex()
    private var whisper: Pair<File, WhisperEngine>? = null
    private var vad: Pair<File, SileroVad>? = null
    private var llm: Pair<File, LlamaEngine>? = null
    private var piper: Pair<File, PiperSynthesizer>? = null
    private var system: SystemSynthesizer? = null

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    fun isWhisperReady(): Boolean = whisper?.first == models.file(ModelKind.WHISPER) && whisper != null

    /** Piper when chosen and installed; otherwise (or if Piper fails to load) the Android TTS fallback. */
    override suspend fun synthesizer(preferPiper: Boolean): SpeechSynthesizer = ttsLock.withLock {
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

    suspend fun llm(): LlamaEngine = llmLock.withLock {
        val file = models.file(ModelKind.LLM) ?: throw MissingModelException(ModelKind.LLM)
        llm?.takeIf { it.first == file }?.let { return it.second }
        llm?.second?.close(); llm = null
        _status.update { it.copy(llm = LoadState.LOADING) }
        try {
            withContext(Dispatchers.IO) { LlamaEngine.load(file, nCtx = LLM_CONTEXT) }.also {
                llm = file to it
                _status.update { s -> s.copy(llm = LoadState.READY) }
            }
        } catch (e: Exception) {
            _status.update { it.copy(llm = LoadState.FAILED, error = e.message) }
            throw e
        }
    }

    suspend fun whisper(): WhisperEngine = whisperLock.withLock {
        val file = models.file(ModelKind.WHISPER) ?: throw MissingModelException(ModelKind.WHISPER)
        whisper?.takeIf { it.first == file }?.let { return it.second }
        whisper?.second?.close(); whisper = null
        _status.update { it.copy(whisper = LoadState.LOADING) }
        try {
            withContext(Dispatchers.IO) { WhisperEngine.load(file) }.also {
                whisper = file to it
                _status.update { s -> s.copy(whisper = LoadState.READY) }
            }
        } catch (e: Exception) {
            _status.update { it.copy(whisper = LoadState.FAILED, error = e.message) }
            throw e
        }
    }

    /** Silero if installed, otherwise the energy-based fallback. */
    suspend fun speechDetector(): SpeechDetector = vadLock.withLock {
        val file = models.file(ModelKind.VAD) ?: return EnergySpeechDetector()
        vad?.takeIf { it.first == file }?.let { return it.second.also { d -> d.reset() } }
        vad?.second?.close()
        runCatching { withContext(Dispatchers.IO) { SileroVad.load(file) } }
            .map { it.also { v -> vad = file to v } as SpeechDetector }
            .getOrElse { EnergySpeechDetector() }
    }

    /**
     * Loads whisper (and the VAD) first, then the LLM, in the background, so the first mic press and the first
     * question do not pay the load time. Failures are reported through [status], not thrown.
     */
    suspend fun preload() {
        runCatching { whisper() }
        runCatching { speechDetector() }
        runCatching { llm() }
    }

    /** Frees native memory (e.g. on onTrimMemory). */
    suspend fun releaseAll() {
        whisperLock.withLock { whisper?.second?.close(); whisper = null }
        vadLock.withLock { vad?.second?.close(); vad = null }
        llmLock.withLock { llm?.second?.close(); llm = null }
        ttsLock.withLock {
            piper?.second?.close(); piper = null
            system?.close(); system = null
        }
        _status.value = EngineStatus()
    }

    companion object {
        const val LLM_CONTEXT = 4096
    }
}
