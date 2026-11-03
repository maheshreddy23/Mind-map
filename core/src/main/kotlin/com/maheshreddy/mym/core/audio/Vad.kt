package com.maheshreddy.mym.core.audio

import kotlin.math.max
import kotlin.math.sqrt

/** Scores a frame of 16 kHz mono audio with a speech probability in [0, 1]. */
interface SpeechDetector {
    fun speechProbability(frame: FloatArray): Float
    fun reset() {}
}

/**
 * Fallback VAD used when the Silero model is not installed. It tracks an adaptive noise floor and
 * maps the frame's RMS-to-floor ratio onto a pseudo probability.
 */
class EnergySpeechDetector(
    private val ratioForSpeech: Float = 3.0f,
    private val minRms: Float = 0.004f,
) : SpeechDetector {
    private var noiseFloor = -1f

    override fun speechProbability(frame: FloatArray): Float {
        val rms = rms(frame)
        if (noiseFloor < 0f) noiseFloor = max(rms, minRms)
        val ratio = rms / max(noiseFloor, minRms)
        // Adapt quickly downwards (quiet room) and slowly upwards (so speech doesn't become "noise").
        noiseFloor = if (rms < noiseFloor) 0.9f * noiseFloor + 0.1f * rms else 0.998f * noiseFloor + 0.002f * rms
        noiseFloor = max(noiseFloor, minRms)
        return ((ratio - 1f) / (ratioForSpeech - 1f)).coerceIn(0f, 1f)
    }

    override fun reset() {
        noiseFloor = -1f
    }

    companion object {
        fun rms(frame: FloatArray): Float {
            if (frame.isEmpty()) return 0f
            var sum = 0.0
            for (s in frame) sum += s * s
            return sqrt(sum / frame.size).toFloat()
        }
    }
}

/**
 * Turns a stream of fixed-size frames into utterances. Speech starts after [minSpeechMs] of
 * frames above [startThreshold], and ends after [endSilenceMs] below [endThreshold] (hysteresis).
 * [preRollMs] of audio before the onset is kept so the first syllable is not clipped.
 */
class Endpointer(
    private val detector: SpeechDetector,
    private val sampleRate: Int = Wav.WHISPER_SAMPLE_RATE,
    val frameSize: Int = 512,
    private val startThreshold: Float = 0.5f,
    private val endThreshold: Float = 0.35f,
    private val minSpeechMs: Int = 250,
    private val endSilenceMs: Int = 900,
    private val preRollMs: Int = 300,
    private val maxUtteranceMs: Int = 28_000,
) {
    sealed interface Event {
        data object SpeechStarted : Event
        class Utterance(val samples: FloatArray) : Event
    }

    private val frameMs = frameSize * 1000 / sampleRate
    private val preRoll = ArrayDeque<FloatArray>()
    private val current = ArrayList<FloatArray>()
    private var inSpeech = false
    private var speechRunMs = 0
    private var silenceRunMs = 0

    val isInSpeech: Boolean get() = inSpeech

    /** Feeds one frame of exactly [frameSize] samples; returns events produced by it. */
    fun accept(frame: FloatArray): List<Event> {
        require(frame.size == frameSize) { "expected $frameSize samples, got ${frame.size}" }
        val p = detector.speechProbability(frame)
        val events = ArrayList<Event>(1)
        if (!inSpeech) {
            preRoll.addLast(frame)
            while (preRoll.size * frameMs > preRollMs + minSpeechMs) preRoll.removeFirst()
            speechRunMs = if (p >= startThreshold) speechRunMs + frameMs else 0
            if (speechRunMs >= minSpeechMs) {
                inSpeech = true
                silenceRunMs = 0
                current.addAll(preRoll)
                preRoll.clear()
                events += Event.SpeechStarted
            }
        } else {
            current.add(frame)
            silenceRunMs = if (p < endThreshold) silenceRunMs + frameMs else 0
            if (silenceRunMs >= endSilenceMs) {
                events += Event.Utterance(drain())
                endSpeech()
            } else if (current.size * frameMs >= maxUtteranceMs) {
                // Whisper works on <= 30 s windows: cut here but stay in speech so the rest is captured.
                events += Event.Utterance(drain())
            }
        }
        return events
    }

    /** Ends any in-progress utterance (e.g. user tapped stop); returns it if it had speech. */
    fun flush(): FloatArray? {
        val out = if (inSpeech && current.isNotEmpty()) drain() else null
        endSpeech()
        preRoll.clear()
        return out
    }

    /** Full reset, including the detector's adaptive state (use when starting a new recording). */
    fun reset() {
        flush()
        detector.reset()
    }

    private fun endSpeech() {
        inSpeech = false
        speechRunMs = 0
        silenceRunMs = 0
    }

    private fun drain(): FloatArray {
        val out = FloatArray(current.sumOf { it.size })
        var at = 0
        for (f in current) {
            f.copyInto(out, at); at += f.size
        }
        current.clear()
        silenceRunMs = 0
        return out
    }
}

/** Splits arbitrary-length sample chunks (as delivered by a recorder) into fixed frames. */
class Framer(private val frameSize: Int) {
    private val pending = FloatArray(frameSize)
    private var filled = 0

    fun push(samples: FloatArray, count: Int = samples.size, onFrame: (FloatArray) -> Unit) {
        var i = 0
        while (i < count) {
            val n = minOf(frameSize - filled, count - i)
            samples.copyInto(pending, filled, i, i + n)
            filled += n; i += n
            if (filled == frameSize) {
                onFrame(pending.copyOf())
                filled = 0
            }
        }
    }
}
