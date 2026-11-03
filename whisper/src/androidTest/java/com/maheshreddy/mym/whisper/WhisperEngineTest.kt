package com.maheshreddy.mym.whisper

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maheshreddy.mym.core.audio.Wav
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperEngineTest {
    @Test
    fun transcribesJfkSample() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val samples = ctx.assets.open("jfk.wav").use { Wav.toWhisperInput(it.readBytes()) }
        val model = TestModels.copy("ggml-tiny.en.bin")
        Log.i("WhisperEngineTest", WhisperEngine.systemInfo())

        WhisperEngine.load(model).use { engine ->
            val start = System.nanoTime()
            val text = engine.transcribe(samples)
            val seconds = (System.nanoTime() - start) / 1e9
            Log.i("WhisperEngineTest", "Transcribed 11 s of audio in %.2f s: %s".format(seconds, text))
            val normalized = text.lowercase().replace(Regex("[^a-z ]"), "")
            assertTrue("unexpected transcript: $text", normalized.contains("ask not what your country can do for you"))
        }
    }

    @Test
    fun loadFailureReportsWhisperReason() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val junk = java.io.File(ctx.cacheDir, "not-a-model.bin").apply { writeBytes(ByteArray(4096) { 7 }) }
        val error = runCatching { WhisperEngine.load(junk) }.exceptionOrNull()
        assertTrue("expected a load failure", error is IllegalStateException)
        assertTrue("reason missing: ${error?.message}", error!!.message!!.contains("bad magic"))
    }
}
