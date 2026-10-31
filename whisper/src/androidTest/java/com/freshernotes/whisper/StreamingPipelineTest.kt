package com.freshernotes.whisper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freshernotes.core.audio.EnergySpeechDetector
import com.freshernotes.core.audio.Endpointer
import com.freshernotes.core.audio.Framer
import com.freshernotes.core.audio.SpeechDetector
import com.freshernotes.core.audio.Wav
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun sileroVadSilenceIsNotSpeech() {
        SileroVad.load(TestModels.copy("ggml-silero-v5.1.2.bin")).use { vad ->
            val p = vad.speechProbability(FloatArray(512))
            assertTrue("silence scored $p", p < 0.3f)
            assertEquals(Unit, vad.reset())
        }
    }
}
