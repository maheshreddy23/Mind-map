package com.maheshreddy.mym.audio

import com.maheshreddy.mym.device.PerfStats
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.core.OfficialChecksums
import com.maheshreddy.mym.core.audio.Endpointer
import com.maheshreddy.mym.core.audio.Framer
import com.maheshreddy.mym.core.audio.TranscriptFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A transcribed utterance with how long the audio was and how long whisper took. */
data class Transcript(val text: String, val audioSeconds: Double, val processingSeconds: Double)

sealed interface ListenState {
    data object Idle : ListenState
    data object LoadingModel : ListenState
    data class Listening(val continuous: Boolean, val hearingSpeech: Boolean) : ListenState
    data object Transcribing : ListenState
    data class Error(val message: String) : ListenState
}

/**
 * Microphone -> (VAD endpointing | push-to-talk buffer) -> whisper -> [transcripts].
 * Transcription runs on a single worker so utterances stay in order.
 */
class SpeechInput @Inject constructor(
    private val recorder: AudioRecorder,
    private val engines: Engines,
    private val perf: PerfStats,
    private val settings: com.maheshreddy.mym.data.settings.SettingsRepository,
) {
    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private val _transcripts = MutableSharedFlow<Transcript>(extraBufferCapacity = 32)
    val transcripts: SharedFlow<Transcript> = _transcripts.asSharedFlow()

    private val _level = MutableStateFlow(0f)
    /** Microphone loudness 0..1 (RMS of the latest chunk, scaled), for the waveform. 0 when not recording. */
    val level: StateFlow<Float> = _level.asStateFlow()

    /** While true (e.g. the fresher is speaking through TTS) captured audio is discarded. */
    @Volatile var muted: Boolean = false

    private var recordJob: Job? = null
    private var workerJob: Job? = null
    private val queue = Channel<FloatArray>(Channel.UNLIMITED)
    private val pttBuffer = ArrayList<FloatArray>()
    private var continuous = false

    fun isRecording(): Boolean = recordJob?.isActive == true

    fun start(scope: CoroutineScope, continuous: Boolean) {
        if (isRecording()) return
        this.continuous = continuous
        ensureWorker(scope)
        synchronized(pttBuffer) { pttBuffer.clear() }
        recordJob = scope.launch {
            try {
                val endpointer = if (continuous) Endpointer(engines.speechDetector()) else null
                val framer = endpointer?.let { Framer(it.frameSize) }
                _state.value = ListenState.Listening(continuous, hearingSpeech = false)
                recorder.record().collect { chunk ->
                    _level.value = if (muted) 0f else levelOf(chunk)
                    if (muted) {
                        endpointer?.reset()
                        return@collect
                    }
                    if (endpointer == null) {
                        synchronized(pttBuffer) { pttBuffer += chunk }
                    } else {
                        framer!!.push(chunk) { frame ->
                            for (e in endpointer.accept(frame)) {
                                if (e is Endpointer.Event.Utterance) queue.trySend(e.samples)
                            }
                        }
                        _state.value = ListenState.Listening(true, endpointer.isInSpeech)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ListenState.Error(e.message ?: "Recording failed")
            }
        }
    }

    /** Stops recording. In push-to-talk mode, the captured audio is transcribed. */
    fun stop() {
        _level.value = 0f
        recordJob?.cancel()
        recordJob = null
        if (!continuous) {
            val audio = synchronized(pttBuffer) {
                val total = pttBuffer.sumOf { it.size }
                FloatArray(total).also { out -> var at = 0; pttBuffer.forEach { it.copyInto(out, at); at += it.size } }
                    .also { pttBuffer.clear() }
            }
            // Ignore taps shorter than ~0.3 s.
            if (audio.size > 4_800) queue.trySend(audio)
        }
        if (_state.value is ListenState.Listening) _state.value = ListenState.Idle
    }

    private fun ensureWorker(scope: CoroutineScope) {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            for (audio in queue) {
                val previous = _state.value
                try {
                    if (!engines.isWhisperReady()) _state.value = ListenState.LoadingModel
                    val engine = engines.whisper()
                    _state.value = ListenState.Transcribing
                    val t0 = System.nanoTime()
                    // Domain vocabulary (Settings → Speech recognition) biases whisper towards the user's jargon.
                    val prefs = settings.settings.first()
                    val text = engine.transcribe(
                        audio,
                        prompt = com.maheshreddy.mym.data.settings.SettingsRepository.whisperPrompt(prefs.vocabulary),
                        language = if (prefs.multilingual) "auto" else "en",
                    )
                    val audioSec = audio.size / 16_000.0
                    val procSec = (System.nanoTime() - t0) / 1e9
                    perf.recordStt(audioSec, procSec)
                    if (TranscriptFilter.isMeaningful(text)) _transcripts.emit(Transcript(TranscriptFilter.clean(text), audioSec, procSec))
                    _state.value = if (isRecording()) previous else ListenState.Idle
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = e.message ?: "Transcription failed"
                    _state.value = ListenState.Error(OfficialChecksums.loadFailureHint(msg)?.let { "$msg\n$it" } ?: msg)
                }
            }
        }
    }

    companion object {
        /** RMS scaled so normal speech (~0.05-0.1 RMS) fills most of the range. */
        internal fun levelOf(chunk: FloatArray): Float {
            if (chunk.isEmpty()) return 0f
            var sum = 0.0
            for (x in chunk) sum += x * x
            return (kotlin.math.sqrt(sum / chunk.size) * 8.0).toFloat().coerceIn(0f, 1f)
        }
    }
}
