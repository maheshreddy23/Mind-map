package com.freshernotes.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sin

class EndpointerTest {
    private val rng = Random(42)
    private fun noise(n: Int, amp: Float = 0.002f) = FloatArray(n) { (rng.nextGaussian() * amp).toFloat() }
    private fun voice(n: Int) = FloatArray(n) { (0.3 * sin(it * 2 * Math.PI * 220 / 16_000)).toFloat() + (rng.nextGaussian() * 0.002).toFloat() }

    private fun run(endpointer: Endpointer, audio: FloatArray): List<Endpointer.Event> {
        val events = ArrayList<Endpointer.Event>()
        Framer(endpointer.frameSize).push(audio) { events += endpointer.accept(it) }
        return events
    }

    @Test
    fun detectsOneUtteranceBetweenSilences() {
        val ep = Endpointer(EnergySpeechDetector())
        val audio = noise(16_000) + voice(32_000) + noise(24_000)
        val events = run(ep, audio)
        assertEquals(1, events.count { it is Endpointer.Event.SpeechStarted })
        val utterances = events.filterIsInstance<Endpointer.Event.Utterance>()
        assertEquals(1, utterances.size)
        val seconds = utterances[0].samples.size / 16_000.0
        // 2 s of voice + up to 0.9 s trailing silence + pre-roll.
        assertTrue("utterance length $seconds", seconds in 2.0..3.6)
    }

    @Test
    fun ignoresShortClicks() {
        val ep = Endpointer(EnergySpeechDetector())
        val events = run(ep, noise(16_000) + voice(1_600) + noise(32_000))
        assertTrue(events.isEmpty())
    }

    @Test
    fun splitsTwoUtterances() {
        val ep = Endpointer(EnergySpeechDetector())
        val events = run(ep, noise(8_000) + voice(16_000) + noise(24_000) + voice(16_000) + noise(24_000))
        assertEquals(2, events.filterIsInstance<Endpointer.Event.Utterance>().size)
    }

    @Test
    fun capsVeryLongUtterances() {
        val ep = Endpointer(EnergySpeechDetector(), maxUtteranceMs = 5_000)
        val events = run(ep, noise(8_000) + voice(16_000 * 12))
        assertTrue(events.filterIsInstance<Endpointer.Event.Utterance>().size >= 2)
    }

    @Test
    fun flushReturnsPartialSpeechOnly() {
        val ep = Endpointer(EnergySpeechDetector())
        run(ep, noise(8_000))
        assertNull(ep.flush())
        run(ep, noise(8_000) + voice(16_000))
        assertTrue((ep.flush()?.size ?: 0) > 16_000)
    }

    @Test
    fun framerEmitsFixedFrames() {
        val sizes = ArrayList<Int>()
        val framer = Framer(512)
        framer.push(FloatArray(700)) { sizes += it.size }
        framer.push(FloatArray(400)) { sizes += it.size }
        assertEquals(listOf(512, 512), sizes)
    }
}
