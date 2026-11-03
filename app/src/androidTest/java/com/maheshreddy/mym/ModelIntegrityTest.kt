package com.maheshreddy.mym

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.models.ModelStore
import com.maheshreddy.mym.core.OfficialChecksums
import com.maheshreddy.mym.whisper.WhisperEngine
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
    fun cleanModelMatchesOfficialChecksum() = runBlocking<Unit> {
        model.delete()
        TestModels.copyTo("ggml-tiny.en.bin", model)
        val result = ModelStore(context).verify(ModelKind.WHISPER)
        assertEquals(OfficialChecksums.known.getValue("ggml-tiny.en.bin").sha256, result.sha256)
        assertEquals(OfficialChecksums.Verdict.MATCHES_OFFICIAL, result.verdict)
    }

    private fun punchHole(atFraction: Double) {
        val size = model.length()
        RandomAccessFile(model, "rw").use { raf ->
            raf.seek((size * atFraction).toLong())
            raf.write(ByteArray(2 shl 20))
        }
        assertEquals(size, model.length())
    }

    /** A hole over tensor headers: reproduces the device error and must carry the re-download hint. */
    @Test
    fun holeOverTensorHeadersFailsWithHint() = runBlocking<Unit> {
        model.delete()
        TestModels.copyTo("ggml-tiny.en.bin", model)
        punchHole(0.8) // verified on host: "unknown tensor '' in model file"
        assertEquals(OfficialChecksums.Verdict.DIFFERS_FROM_OFFICIAL, ModelStore(context).verify(ModelKind.WHISPER).verdict)
        val error = runCatching { WhisperEngine.load(model) }.exceptionOrNull()
        assertNotNull("damaged model must not load", error)
        assertTrue(error!!.message!!, error.message!!.contains("unknown tensor"))
        assertNotNull(OfficialChecksums.loadFailureHint(error.message!!))
        model.delete()
    }

    /** A hole inside one tensor's data loads without error (silent corruption): only the checksum catches it. */
    @Test
    fun holeInsideTensorDataIsCaughtOnlyByChecksum() = runBlocking<Unit> {
        model.delete()
        TestModels.copyTo("ggml-tiny.en.bin", model)
        punchHole(0.5) // verified on host: loads successfully despite the damage
        assertEquals(OfficialChecksums.Verdict.DIFFERS_FROM_OFFICIAL, ModelStore(context).verify(ModelKind.WHISPER).verdict)
        model.delete()
    }
}
