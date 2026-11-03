package com.maheshreddy.mym

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maheshreddy.mym.core.models.Catalog
import com.maheshreddy.mym.models.DownloadState
import com.maheshreddy.mym.models.ModelDownloader
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.models.ModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * The model downloader against Hugging Face with the smallest catalogue model (Silero VAD, 885 KB): full download
 * with SHA-256 check and activation, resume of a half-finished file with an HTTP Range request, and a checksum
 * mismatch that must delete the file and never install it. Full flavour only (the Sealed build has no network).
 */
@RunWith(AndroidJUnit4::class)
class DownloaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: ModelStore
    private lateinit var downloader: ModelDownloader
    private val silero = Catalog.byId("silero-vad-5.1.2")!!

    @Before
    fun setUp() {
        store = ModelStore(context)
        store.catalogDir(silero.id).deleteRecursively()
        store.catalogDir("bad-silero").deleteRecursively()
        store.refresh()
        downloader = ModelDownloader(context, store)
    }

    @Test
    fun downloadsVerifiesActivatesAndResumes() = runBlocking {
        downloader.download(silero, activate = true)
        val file = File(store.catalogDir(silero.id), silero.files[0].fileName)
        assertEquals(silero.files[0].size, file.length())
        assertEquals(DownloadState.Status.DONE, downloader.states.value[silero.id]?.status)
        assertEquals(silero.id, store.activeId(ModelKind.VAD))
        assertNotNull(store.file(ModelKind.VAD))

        // Simulate an interrupted download: keep the first half as a .part file and download again.
        val part = File(file.parentFile, file.name + ".part")
        file.renameTo(part)
        RandomAccessFile(part, "rw").use { it.setLength(silero.files[0].size / 2) }
        File(file.parentFile, ".complete").delete()
        assertEquals(silero.files[0].size / 2, downloader.partialBytes(silero))
        downloader.download(silero)
        assertEquals(silero.files[0].size, file.length())
        assertFalse(part.exists())
        assertTrue(File(file.parentFile, ".complete").isFile)
    }

    @Test
    fun checksumMismatchDeletesTheFile() = runBlocking {
        val bad = silero.copy(id = "bad-silero", files = listOf(silero.files[0].copy(hash = "0".repeat(64))))
        try {
            downloader.download(bad)
            fail("a file with the wrong checksum must not install")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("checksum"))
        }
        val dir = store.catalogDir(bad.id)
        assertFalse(File(dir, bad.files[0].fileName).exists())
        assertFalse(File(dir, bad.files[0].fileName + ".part").exists())
        assertFalse(File(dir, ".complete").exists())
        assertEquals(DownloadState.Status.FAILED, downloader.states.value[bad.id]?.status)
    }
}
