package com.freshernotes.whisper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshernotes.core.audio.EnergySpeechDetector
import com.freshernotes.core.audio.Endpointer
import com.freshernotes.core.audio.Framer
import com.freshernotes.core.audio.SpeechDetector
import com.freshernotes.core.audio.Wav
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

    @Test
    fun sileroVadSeparatesSilenceFromSpeech() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val speech = ctx.assets.open("jfk.wav").use { Wav.toWhisperInput(it.readBytes()) }
        SileroVad.load(TestModels.copy("ggml-silero-v5.1.2.bin")).use { vad ->
            // A recurrent VAD needs a few windows of context: the first window after a reset is uninformed (~0.45),
            // which is why the Endpointer requires 250 ms of consecutive speech before triggering.
            val silenceProbs = (0 until 32).map { vad.speechProbability(FloatArray(512)) }
            assertTrue("silence after warm-up scored ${silenceProbs.takeLast(8)}", silenceProbs.takeLast(8).all { it < 0.3f })
            vad.reset()
            val speechProbs = (0 until speech.size / 512).map { i -> vad.speechProbability(speech.copyOfRange(i * 512, i * 512 + 512)) }
            val voiced = speechProbs.count { it > 0.5f }.toFloat() / speechProbs.size
            assertTrue("only ${voiced * 100}% of JFK frames scored as speech", voiced > 0.5f)
        }
    }
}
