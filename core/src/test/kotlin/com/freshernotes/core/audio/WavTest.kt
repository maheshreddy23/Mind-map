package com.freshernotes.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

class WavTest {
    @Test
    fun roundTrips16BitPcm() {
        val tone = FloatArray(1600) { (0.5 * sin(it / 10.0)).toFloat() }
        val decoded = Wav.decode(Wav.encode16(tone, 16_000))
        assertEquals(16_000, decoded.sampleRate)
        assertEquals(tone.size, decoded.samples.size)
        assertTrue(tone.indices.all { abs(tone[it] - decoded.samples[it]) < 1e-3 })
    }

    @Test
    fun resamplesTo16k() {
        val audio = PcmAudio(FloatArray(48_000) { 0.1f }, 48_000)
        val out = Wav.resample(audio)
        assertEquals(16_000, out.sampleRate)
        assertEquals(16_000, out.samples.size)
    }

    @Test
    fun decodesWhisperSampleJfk() {
        val f = File("../third_party/whisper.cpp/samples/jfk.wav")
        assumeTrue("submodule not checked out", f.isFile)
        val audio = Wav.decode(f.readBytes())
        assertEquals(16_000, audio.sampleRate)
        assertEquals(11.0, audio.durationSeconds, 0.1)
    }
}
