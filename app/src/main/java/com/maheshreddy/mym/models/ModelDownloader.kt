package com.maheshreddy.mym.models

import android.content.Context
import android.system.Os
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.maheshreddy.mym.BuildConfig
import com.maheshreddy.mym.core.models.Catalog
import com.maheshreddy.mym.core.models.CatalogModel
import com.maheshreddy.mym.core.models.Checksums
import com.maheshreddy.mym.core.models.RemoteFile
import com.maheshreddy.mym.core.models.SharedData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class DownloadState(
    val modelId: String,
    val status: Status,
    val bytes: Long = 0,
    val total: Long = 0,
    val error: String? = null,
) {
    enum class Status { WAITING, DOWNLOADING, PAUSED, VERIFYING, DONE, FAILED }
    val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Downloads catalogue models from Hugging Face over HTTPS into app-private storage. Files are pinned to the
 * catalogue revision, resumed with HTTP Range after a pause or a lost connection, and checked against their
 * official hash before the model is activated; a file that does not match is deleted. Network is used for nothing
 * else. The Sealed build has no INTERNET permission, so downloads are unavailable there ([available] is false).
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ModelStore,
) {
    val available: Boolean = BuildConfig.MODEL_DOWNLOADS

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private fun set(s: DownloadState) = _states.update { it + (s.modelId to s) }

    /** Queues a download in the background (WorkManager). Wi-Fi only unless [allowMobileData]. */
    fun enqueue(id: String, allowMobileData: Boolean) {
        check(available) { "This build cannot download models. Import the file instead." }
        val m = requireNotNull(Catalog.byId(id)) { "Unknown model $id" }
        set(DownloadState(id, DownloadState.Status.WAITING, partialBytes(m), m.sizeBytes))
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    /** Stops the download and keeps what was downloaded so far. */
    fun pause(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        val m = Catalog.byId(id) ?: return
        set(DownloadState(id, DownloadState.Status.PAUSED, partialBytes(m), m.sizeBytes))
    }

    /** Stops the download and deletes the partial files. */
    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        if (store.models.value.none { it.id == id }) store.catalogDir(id).deleteRecursively()
        _states.update { it - id }
    }

    fun partialBytes(m: CatalogModel): Long {
        val d = store.catalogDir(m.id)
        return m.files.sumOf { f -> File(d, f.fileName).takeIf { it.isFile }?.length() ?: File(d, f.fileName + ".part").length() }
    }

    /**
     * Downloads, verifies and installs [m]; [activate] makes it the active model of its slot. Runs in the caller's coroutine (the worker, or a test); cancelling
     * it leaves .part files for a later resume. Returns when the model is installed.
     */
    suspend fun download(m: CatalogModel, activate: Boolean = false) = withContext(Dispatchers.IO) {
        val dir = store.catalogDir(m.id).apply { mkdirs() }
        File(dir, ".complete").delete()
        val shared = if (m.sharedData == SharedData.ESPEAK_NG) espeakManifest() else emptyList()
        val total = m.sizeBytes + shared.sumOf { it.size }
        var done = 0L
        try {
            for (f in m.files) {
                val target = File(dir, f.fileName)
                if (target.isFile && target.length() == f.size) { done += f.size; continue }
                fetch(f.url(m.repo, m.revision), File(dir, f.fileName + ".part"), f.size) { n -> set(DownloadState(m.id, DownloadState.Status.DOWNLOADING, done + n, total)) }
                set(DownloadState(m.id, DownloadState.Status.VERIFYING, done + f.size, total))
                val part = File(dir, f.fileName + ".part")
                if (!Checksums.verify(part, f)) {
                    part.delete()
                    throw IOException("${f.fileName} does not match its official checksum. The download was damaged and has been deleted; please try again.")
                }
                check(part.renameTo(target)) { "Could not save ${f.fileName}" }
                done += f.size
            }
            if (shared.isNotEmpty()) {
                installEspeak(shared) { n -> set(DownloadState(m.id, DownloadState.Status.DOWNLOADING, done + n, total)) }
                val link = File(dir, "espeak-ng-data")
                if (!link.exists()) Os.symlink(store.espeakDir.absolutePath, link.absolutePath)
                done += shared.sumOf { it.size }
            }
            File(dir, ".complete").writeText(m.files.joinToString("\n") { it.hash })
            store.refresh()
            // Becomes active when asked (onboarding) or when its slot had no model; otherwise the user switches.
            if (activate || store.models.value.count { it.kind == ModelKind.of(m.category) } == 1) store.activate(m.id)
            set(DownloadState(m.id, DownloadState.Status.DONE, total, total))
        } catch (e: kotlinx.coroutines.CancellationException) {
            set(DownloadState(m.id, DownloadState.Status.PAUSED, partialBytes(m), total))
            throw e
        } catch (e: Exception) {
            set(DownloadState(m.id, DownloadState.Status.FAILED, partialBytes(m), total, e.message ?: e.javaClass.simpleName))
            throw e
        }
    }

    private fun espeakManifest(): List<RemoteFile> =
        Catalog.parseManifest(context.assets.open("catalog/espeak-ng-data.tsv").bufferedReader().use { it.readText() })

    /** espeak-ng-data is downloaded once (verified per file against Hugging Face's Git hashes) and shared. */
    private suspend fun installEspeak(files: List<RemoteFile>, onBytes: (Long) -> Unit) {
        val root = store.espeakDir.parentFile!!
        val staging = File(root, "espeak-ng-data.partial")
        var done = 0L
        for (f in files) {
            val rel = f.path.removePrefix("espeak-ng-data/")
            val installed = File(store.espeakDir, rel)
            if (installed.isFile && installed.length() == f.size) { done += f.size; onBytes(done); continue }
            val target = File(staging, rel).apply { parentFile?.mkdirs() }
            if (!(target.isFile && Checksums.verify(target, f))) {
                val part = File(target.path + ".part")
                fetch(f.url(Catalog.ESPEAK_REPO, Catalog.ESPEAK_REVISION), part, f.size) { onBytes(done + it) }
                if (!Checksums.verify(part, f)) { part.delete(); throw IOException("espeak-ng-data/$rel failed its checksum; please try again.") }
                check(part.renameTo(target))
            }
            done += f.size
            onBytes(done)
        }
        if (staging.isDirectory) {
            store.espeakDir.mkdirs()
            staging.walkTopDown().filter { it.isFile }.forEach { src ->
                val dest = File(store.espeakDir, src.relativeTo(staging).path).apply { parentFile?.mkdirs() }
                check(src.renameTo(dest))
            }
            staging.deleteRecursively()
        }
    }

    /** HTTPS GET into [part], resuming from its current length; follows Hugging Face's CDN redirects. */
    private suspend fun fetch(url: String, part: File, size: Long, onBytes: (Long) -> Unit) {
        var have = if (part.isFile) part.length() else 0L
        if (have > size) { part.delete(); have = 0 }
        if (have == size) return
        var location = url
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (URL(location).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "MYM/${BuildConfig.VERSION_NAME} (Android)")
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                conn.disconnect()
                location = URL(URL(location), next).toString()
                require(location.startsWith("https://")) { "Refusing a non-HTTPS download location" }
                if (++hops > 8) throw IOException("Too many redirects")
                continue
            }
            if (have > 0 && code == 200) { have = 0; part.delete() } // server ignored Range: start over
            if (code != 200 && code != 206) throw IOException("Download failed: HTTP $code")
            break
        }
        part.parentFile?.mkdirs()
        RandomAccessFile(part, "rw").use { out ->
            out.seek(have)
            conn.inputStream.use { input ->
                val buf = ByteArray(1 shl 16)
                var got = have
                var lastReport = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    got += n
                    if (got - lastReport > 256 * 1024 || got == size) { onBytes(got); lastReport = got }
                }
                if (got != size) throw IOException("Connection closed early ($got of $size bytes); it will resume from here.")
                onBytes(got)
            }
        }
        conn.disconnect()
    }

    companion object {
        const val TAG = "model-download"
        fun workName(id: String) = "download-$id"
    }
}
