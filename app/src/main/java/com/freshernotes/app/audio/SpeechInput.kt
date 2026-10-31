package com.freshernotes.app.audio

import com.freshernotes.app.device.PerfStats
import com.freshernotes.app.engine.Engines
import com.freshernotes.core.audio.Endpointer
import com.freshernotes.core.audio.Framer
import com.freshernotes.core.audio.TranscriptFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ListenState {
    data object Idle : ListenState
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
) {
    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private val _transcripts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val transcripts: SharedFlow<String> = _transcripts.asSharedFlow()

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
                _state.value = ListenState.Transcribing
                try {
                    val engine = engines.whisper()
                    val t0 = System.nanoTime()
                    val text = engine.transcribe(audio, prompt = DOMAIN_PROMPT)
                    perf.recordStt(audio.size / 16_000.0, (System.nanoTime() - t0) / 1e9)
                    if (TranscriptFilter.isMeaningful(text)) _transcripts.emit(TranscriptFilter.clean(text))
                    _state.value = if (isRecording()) previous else ListenState.Idle
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = ListenState.Error(e.message ?: "Transcription failed")
                }
            }
        }
    }

    companion object {
        /** Biases whisper towards the user's vocabulary. */
        const val DOMAIN_PROMPT =
            "Aerospace manufacturing engineering: jigs, fixtures, tooling, PLM, CATIA, NX, Teamcenter, BOM, GD&T, " +
                "ECO, work instructions, datums, drilling templates, assembly jigs."
    }
}
