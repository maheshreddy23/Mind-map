package com.maheshreddy.mym.whisper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maheshreddy.mym.core.audio.EnergySpeechDetector
import com.maheshreddy.mym.core.audio.Endpointer
import com.maheshreddy.mym.core.audio.Framer
import com.maheshreddy.mym.core.audio.SpeechDetector
import com.maheshreddy.mym.core.audio.Wav
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulates live capture: jfk.wav surrounded by silence is fed frame by frame (as AudioRecord would)
 * through the VAD endpointer, and each detected utterance goes to whisper.
 */
@RunWith(AndroidJUnit4::class)
class StreamingPipelineTest {
    private fun jfkWithSilence(): FloatArray {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val speech = ctx.assets.open("jfk.wav").use { Wav.toWhisperInput(it.readBytes()) }
        val silence = FloatArray(16_000 * 2)
        return silence + speech + silence
    }

    private fun runPipeline(detector: SpeechDetector): List<String> = runBlocking {
        val endpointer = Endpointer(detector, endSilenceMs = 1_200)
        val utterances = ArrayList<FloatArray>()
        Framer(endpointer.frameSize).push(jfkWithSilence()) { frame ->
            endpointer.accept(frame).filterIsInstance<Endpointer.Event.Utterance>().forEach { utterances += it.samples }
        }
        endpointer.flush()?.let { utterances += it }
        WhisperEngine.load(TestModels.copy("ggml-tiny.en.bin")).use { engine ->
            utterances.map { engine.transcribe(it) }
        }
    }

    @Test
    fun sileroVadSegmentsAndTranscribes() {
        val texts = SileroVad.load(TestModels.copy("ggml-silero-v5.1.2.bin")).use { runPipeline(it) }
        assertTrue("expected speech segments", texts.isNotEmpty())
        val all = texts.joinToString(" ").lowercase().replace(Regex("[^a-z ]"), "")
        assertTrue("unexpected transcript: $texts", all.contains("your country"))
    }

    @Test
    fun energyVadSegmentsAndTranscribes() {
        val texts = runPipeline(EnergySpeechDetector())
        assertTrue(texts.isNotEmpty())
        val all = texts.joinToString(" ").lowercase().replace(Regex("[^a-z ]"), "")
        assertTrue("unexpected transcript: $texts", all.contains("country"))
    }

    /**
     * Checks the contract the Endpointer relies on (start threshold 0.5, end threshold 0.35):
     * room-level noise never starts speech, real speech does (8+ consecutive frames >= 0.5), and silence right after speech
     * falls below the end threshold so utterances terminate.
     * Note: from a freshly reset state Silero maps exact digital zeros to a fixed ~0.45, which is
     * below the start threshold; real microphones never deliver exact zeros, so noise is used here.
     */
    @Test
    fun sileroVadMeetsEndpointerThresholds() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val speech = ctx.assets.open("jfk.wav").use { Wav.toWhisperInput(it.readBytes()) }
        val rng = java.util.Random(7)
        fun noise() = FloatArray(512) { (rng.nextGaussian() * 0.001).toFloat() } // about -60 dBFS
        SileroVad.load(TestModels.copy("ggml-silero-v5.1.2.bin")).use { vad ->
            val quiet = (0 until 32).map { vad.speechProbability(noise()) }
            assertTrue("noise would start speech: max ${quiet.max()}", quiet.all { it < 0.5f })

            val speechProbs = (0 until speech.size / 512).map { i -> vad.speechProbability(speech.copyOfRange(i * 512, i * 512 + 512)) }
            // The Endpointer starts an utterance after 250 ms (8 frames of 32 ms) at or above 0.5.
            var run = 0
            var longest = 0
            for (p in speechProbs) { run = if (p >= 0.5f) run + 1 else 0; longest = maxOf(longest, run) }
            assertTrue("speech never held >= 0.5 for 250 ms (longest run $longest frames)", longest >= 8)

            val after = (0 until 32).map { vad.speechProbability(noise()) }
            assertTrue("silence after speech never fell below the end threshold: ${after.takeLast(8)}", after.takeLast(8).all { it < 0.35f })
        }
    }
}
