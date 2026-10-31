package com.freshernotes.tts

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PiperSynthesizerTest {
    /** Copies /data/local/tmp/models/<dir> (pushed by CI) into the app cache, file by file via the shell. */
    private fun copyDir(name: String): File {
        val inst = InstrumentationRegistry.getInstrumentation()
        val dest = File(inst.targetContext.cacheDir, name)
        if (File(dest, ".complete").isFile) return dest
        val root = "/data/local/tmp/models/$name"
        val listing = shell("find $root -type f").lines().map { it.trim() }.filter { it.startsWith(root) }
        check(listing.isNotEmpty()) { "Test voice $root is missing. Run scripts/push-test-models.sh first." }
        for (path in listing) {
            val out = File(dest, path.removePrefix("$root/")).apply { parentFile?.mkdirs() }
            ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cat $path")).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        File(dest, ".complete").writeText("ok")
        return dest
    }

    private fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
    }

    @Test
    fun synthesizesAQuestion() = runBlocking {
        val voice = copyDir("vits-piper-en_US-amy-low")
        assertNotNull(PiperSynthesizer.locate(voice))
        PiperSynthesizer.load(voice).use { piper ->
            val audio = piper.synthesize("Why do we locate the drill jig on tooling holes?")
            assertTrue("sample rate ${audio.sampleRate}", audio.sampleRate in 16_000..24_000)
            assertTrue("too short: ${audio.durationSeconds}s", audio.durationSeconds in 1.0..10.0)
            assertTrue("silent output", audio.samples.any { kotlin.math.abs(it) > 0.05f })
        }
    }

    @Test
    fun splitsSentences() {
        assertEquals(listOf("One.", "Two?", "Three"), PiperSynthesizer.splitSentences("One. Two?  Three"))
    }
}
