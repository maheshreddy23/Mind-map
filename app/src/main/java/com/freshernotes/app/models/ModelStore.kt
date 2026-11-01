package com.freshernotes.app.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.freshernotes.core.ModelFileCheck
import com.freshernotes.core.OfficialChecksums
import java.security.MessageDigest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Model slots the app needs. Files are copied from user storage (SAF) into app-private storage. */
enum class ModelKind(
    val label: String,
    val required: Boolean,
    val isDirectory: Boolean,
    val hint: String,
    val format: ModelFileCheck.Format,
) {
    WHISPER("Speech-to-text (whisper)", true, false, "ggml-base.en.bin or ggml-small.en.bin", ModelFileCheck.Format.GGML),
    VAD("Voice activity detection (Silero)", false, false, "ggml-silero-v5.1.2.bin (optional; energy VAD otherwise)", ModelFileCheck.Format.GGML),
    LLM("Language model (GGUF)", true, false, "qwen2.5-3b-instruct-q4_k_m.gguf", ModelFileCheck.Format.GGUF),
    TTS(
        "Piper voice (folder)", false, true,
        "folder with *.onnx, tokens.txt and espeak-ng-data/ (optional; Android TTS otherwise)", ModelFileCheck.Format.ONNX_FOLDER,
    ),
}

data class ImportProgress(val kind: ModelKind, val copiedBytes: Long, val totalBytes: Long, val verifying: Boolean = false)

/** SHA-256 of an installed model file and how it compares with the official checksum. */
data class ModelIntegrity(val fileName: String, val sizeBytes: Long, val sha256: String, val verdict: OfficialChecksums.Verdict)

@Singleton
class ModelStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.filesDir, "models")
    private val _installed = MutableStateFlow(scan())
    val installed: StateFlow<Map<ModelKind, File>> = _installed.asStateFlow()

    private val hashDir = File(root, ".hashes")
    private val _integrity = MutableStateFlow(loadIntegrity())
    /** Known integrity results, from import or from an explicit [verify]. */
    val integrity: StateFlow<Map<ModelKind, ModelIntegrity>> = _integrity.asStateFlow()

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    fun file(kind: ModelKind): File? = _installed.value[kind]

    val missingRequired: List<ModelKind> get() = ModelKind.entries.filter { it.required && file(it) == null }

    private fun dir(kind: ModelKind) = File(root, kind.name.lowercase())

    private fun scan(): Map<ModelKind, File> = ModelKind.entries.mapNotNull { kind ->
        val d = dir(kind)
        val entry = if (kind.isDirectory) d.takeIf { it.isDirectory && !it.list().isNullOrEmpty() }
        else d.listFiles()?.firstOrNull { it.isFile && !it.name.endsWith(".part") }
        entry?.let { kind to it }
    }.toMap()

    /** Copies a single file (or, for directory kinds, a whole tree) into private storage, replacing the old one. */
    suspend fun import(kind: ModelKind, uri: Uri) = withContext(Dispatchers.IO) {
        val target = dir(kind)
        val staging = File(root, "${kind.name.lowercase()}.staging").apply { deleteRecursively(); mkdirs() }
        try {
            if (kind.isDirectory) {
                val tree = requireNotNull(DocumentFile.fromTreeUri(context, uri)) { "Cannot open folder" }
                val total = treeSize(tree)
                var copied = 0L
                copyTree(tree, staging) { n -> copied += n; _progress.value = ImportProgress(kind, copied, total) }
            } else {
                val name = displayName(uri) ?: "${kind.name.lowercase()}.bin"
                val total = size(uri)
                var copied = 0L
                val sourceDigest = MessageDigest.getInstance("SHA-256")
                val file = File(staging, name)
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open $uri" }
                    file.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            sourceDigest.update(buf, 0, n)
                            copied += n
                            _progress.value = ImportProgress(kind, copied, total)
                        }
                        out.fd.sync()
                    }
                }
                if (total > 0 && copied != total) {
                    throw IllegalStateException("Read $copied of $total bytes from $name; the source file may still be downloading.")
                }
                val header = file.inputStream().use { input -> ByteArray(16).let { b -> b.copyOf(maxOf(0, input.read(b))) } }
                val headerCheck = ModelFileCheck.check(kind.format, header, file.length(), file.name)
                if (headerCheck is ModelFileCheck.Result.Bad) throw IllegalArgumentException(headerCheck.message)

                // 1) The copy must be byte-identical to what was read from the source.
                val sourceSha = sourceDigest.digest().toHex()
                _progress.value = ImportProgress(kind, 0, file.length(), verifying = true)
                val copySha = sha256(file) { n -> _progress.value = ImportProgress(kind, n, file.length(), verifying = true) }
                check(copySha == sourceSha) { "The copy of $name is corrupted (SHA-256 $copySha, source $sourceSha). Please try again." }
                // 2) The source itself must match the official file, when we know it.
                val verdict = OfficialChecksums.verdict(name, copySha)
                if (verdict == OfficialChecksums.Verdict.DIFFERS_FROM_OFFICIAL) {
                    val official = OfficialChecksums.known.getValue(name.lowercase())
                    throw IllegalArgumentException(
                        "$name does not match the official file (SHA-256 ${copySha.take(12)}…, expected ${official.sha256.take(12)}…). " +
                            "The download is damaged; download it again.",
                    )
                }
                saveIntegrity(kind, ModelIntegrity(name, file.length(), copySha, verdict))
            }
            target.deleteRecursively()
            check(staging.renameTo(target)) { "Could not move model into place" }
        } finally {
            staging.deleteRecursively()
            _progress.value = null
            _installed.value = scan()
        }
    }

    fun remove(kind: ModelKind) {
        dir(kind).deleteRecursively()
        File(hashDir, kind.name).delete()
        _integrity.value = loadIntegrity()
        _installed.value = scan()
    }

    /** Hashes the installed file and compares it with the official checksum (for models imported earlier). */
    suspend fun verify(kind: ModelKind): ModelIntegrity = withContext(Dispatchers.IO) {
        val file = requireNotNull(file(kind)?.takeIf { it.isFile }) { "${kind.label} is not installed" }
        try {
            val sha = sha256(file) { n -> _progress.value = ImportProgress(kind, n, file.length(), verifying = true) }
            ModelIntegrity(file.name, file.length(), sha, OfficialChecksums.verdict(file.name, sha)).also { saveIntegrity(kind, it) }
        } finally {
            _progress.value = null
        }
    }

    private fun saveIntegrity(kind: ModelKind, i: ModelIntegrity) {
        hashDir.mkdirs()
        File(hashDir, kind.name).writeText("${i.fileName}\n${i.sizeBytes}\n${i.sha256}\n${i.verdict.name}")
        _integrity.value = loadIntegrity()
    }

    private fun loadIntegrity(): Map<ModelKind, ModelIntegrity> = ModelKind.entries.mapNotNull { kind ->
        val lines = File(hashDir, kind.name).takeIf { it.isFile }?.readLines() ?: return@mapNotNull null
        runCatching { kind to ModelIntegrity(lines[0], lines[1].toLong(), lines[2], OfficialChecksums.Verdict.valueOf(lines[3])) }.getOrNull()
    }.toMap()

    private fun sha256(file: File, onProgress: (Long) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        var done = 0L
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                onProgress(done)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.replace('/', '_')

    private fun size(uri: Uri): Long =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L

    private fun treeSize(doc: DocumentFile): Long =
        if (doc.isDirectory) doc.listFiles().sumOf { treeSize(it) } else doc.length()

    private fun copyTree(doc: DocumentFile, dest: File, onBytes: (Int) -> Unit) {
        for (child in doc.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                copyTree(child, File(dest, name).apply { mkdirs() }, onBytes)
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    File(dest, name).outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            onBytes(n)
                        }
                    }
                }
            }
        }
    }
}
