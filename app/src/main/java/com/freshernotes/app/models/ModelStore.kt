package com.freshernotes.app.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.freshernotes.core.ModelFileCheck
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

data class ImportProgress(val kind: ModelKind, val copiedBytes: Long, val totalBytes: Long)

@Singleton
class ModelStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.filesDir, "models")
    private val _installed = MutableStateFlow(scan())
    val installed: StateFlow<Map<ModelKind, File>> = _installed.asStateFlow()

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
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open $uri" }
                    File(staging, name).outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            copied += n
                            _progress.value = ImportProgress(kind, copied, total)
                        }
                    }
                }
            }
            if (!kind.isDirectory) {
                val file = requireNotNull(staging.listFiles()?.firstOrNull()) { "Nothing was copied" }
                val header = file.inputStream().use { input -> ByteArray(16).let { b -> b.copyOf(maxOf(0, input.read(b))) } }
                val result = ModelFileCheck.check(kind.format, header, file.length(), file.name)
                if (result is ModelFileCheck.Result.Bad) throw IllegalArgumentException(result.message)
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
        _installed.value = scan()
    }

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
