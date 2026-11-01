package com.freshernotes.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshernotes.app.models.ModelKind
import com.freshernotes.app.models.ModelStore
import com.freshernotes.core.OfficialChecksums
import com.freshernotes.whisper.WhisperEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * Reproduces the on-device failure "unknown tensor '' in model file": a model with the right size but a
 * zero-filled hole (as left by an interrupted download). Verify must flag it; a clean copy must match.
 */
@RunWith(AndroidJUnit4::class)
class ModelIntegrityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val model = File(context.filesDir, "models/whisper/ggml-tiny.en.bin")

    @Test
    fun cleanModelMatchesOfficialChecksum() = runBlocking {
        model.delete()
        TestModels.copyTo("ggml-tiny.en.bin", model)
        val result = ModelStore(context).verify(ModelKind.WHISPER)
        assertEquals(OfficialChecksums.known.getValue("ggml-tiny.en.bin").sha256, result.sha256)
        assertEquals(OfficialChecksums.Verdict.MATCHES_OFFICIAL, result.verdict)
    }

    @Test
    fun zeroFilledHoleIsDetectedAndExplained() = runBlocking {
        model.delete()
        TestModels.copyTo("ggml-tiny.en.bin", model)
        val size = model.length()
        RandomAccessFile(model, "rw").use { raf ->
            raf.seek(size / 2)
            raf.write(ByteArray(2 shl 20))
        }
        assertEquals(size, model.length())

        val result = ModelStore(context).verify(ModelKind.WHISPER)
        assertEquals(OfficialChecksums.Verdict.DIFFERS_FROM_OFFICIAL, result.verdict)

        val error = runCatching { WhisperEngine.load(model) }.exceptionOrNull()
        assertNotNull("damaged model must not load", error)
        assertNotNull("no damaged-file hint for: ${error!!.message}", OfficialChecksums.loadFailureHint(error.message!!))
        model.delete()
    }
}
