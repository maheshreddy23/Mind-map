package com.maheshreddy.mym.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.maheshreddy.mym.core.ModelFileCheck
import com.maheshreddy.mym.core.OfficialChecksums
import com.maheshreddy.mym.core.models.Catalog
import com.maheshreddy.mym.core.models.CatalogModel
import com.maheshreddy.mym.core.models.ModelCategory
import com.maheshreddy.mym.core.models.SharedData
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

/**
 * Model slots the app uses, one active model each. Models come from the in-app catalogue (downloaded and
 * checksum-verified) or are imported from user storage (SAF); both live in app-private storage (filesDir/models).
 */
enum class ModelKind(
    val label: String,
    val required: Boolean,
    val isDirectory: Boolean,
    val hint: String,
    val format: ModelFileCheck.Format,
    val category: ModelCategory,
) {
    WHISPER("Speech-to-text (whisper)", true, false, "ggml-base.en.bin or ggml-small.en.bin", ModelFileCheck.Format.GGML, ModelCategory.SPEECH),
    VAD("Voice activity detection (Silero)", false, false, "ggml-silero-v5.1.2.bin (optional; energy VAD otherwise)", ModelFileCheck.Format.GGML, ModelCategory.VAD),
    LLM("Language model (GGUF)", true, false, "a GGUF chat model, e.g. Qwen3-4B-Instruct-2507-Q4_K_M.gguf", ModelFileCheck.Format.GGUF, ModelCategory.LLM),
    TTS(
        "Piper voice (folder)", false, true,
        "folder with *.onnx, tokens.txt and espeak-ng-data/ (optional; Android TTS otherwise)", ModelFileCheck.Format.ONNX_FOLDER, ModelCategory.VOICE,
    ),
    EMBEDDING("Embeddings (GGUF)", false, false, "a GGUF sentence-embedding model, e.g. bge-small-en-v1.5-q8_0.gguf (optional; keyword search otherwise)", ModelFileCheck.Format.GGUF, ModelCategory.EMBEDDING);

    companion object {
        fun of(c: ModelCategory) = entries.first { it.category == c }
    }
}

/** A model present on the phone: from the catalogue ([catalog] set) or imported ([catalog] null). */
data class InstalledModel(
    val id: String,
    val kind: ModelKind,
    val name: String,
    /** The file engines load (the voice folder for TTS). */
    val main: File,
    val dir: File,
    val sizeBytes: Long,
    val catalog: CatalogModel?,
    /** True when every file matched its official checksum. */
    val verified: Boolean,
)

data class ImportProgress(val kind: ModelKind, val copiedBytes: Long, val totalBytes: Long, val verifying: Boolean = false)

/** SHA-256 of an installed model file and how it compares with the official checksum. */
data class ModelIntegrity(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val verdict: OfficialChecksums.Verdict,
    /** The official file this one matched (by hash) or claims to be (by normalized name). */
    val officialName: String? = null,
)

@Singleton
class ModelStore @Inject constructor(@ApplicationContext private val context: Context) {
    /** All models live here (app-private, never shared storage). */
    val root = File(context.filesDir, "models")
    val catalogRoot = File(root, "catalog")
    /** espeak-ng-data shared by all Piper voices. */
    val espeakDir = File(root, "shared/espeak-ng-data")
    private val activeFile = File(root, "active.properties")

    private val _models = MutableStateFlow(scanAll())
    /** Every installed model (catalogue and imported). */
    val models: StateFlow<List<InstalledModel>> = _models.asStateFlow()

    private val _installed = MutableStateFlow(scan())
    /** The active model's file per slot (what the engines load). */
    val installed: StateFlow<Map<ModelKind, File>> = _installed.asStateFlow()

    private val hashDir = File(root, ".hashes")
    private val _integrity = MutableStateFlow(loadIntegrity())
    /** Known integrity results, from import or from an explicit [verify]. */
    val integrity: StateFlow<Map<ModelKind, ModelIntegrity>> = _integrity.asStateFlow()

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    fun file(kind: ModelKind): File? = _installed.value[kind]

    val missingRequired: List<ModelKind> get() = ModelKind.entries.filter { it.required && file(it) == null }

    /** Id of the active model per slot ("import:<KIND>" for an imported file). */
    fun activeId(kind: ModelKind): String? = active()[kind] ?: _models.value.firstOrNull { it.kind == kind }?.id

    fun active(kind: ModelKind): InstalledModel? = activeId(kind)?.let { id -> _models.value.firstOrNull { it.id == id } }

    private fun dir(kind: ModelKind) = File(root, kind.name.lowercase())
    fun catalogDir(id: String) = File(catalogRoot, id)

    private fun active(): Map<ModelKind, String> = runCatching {
        java.util.Properties().apply { activeFile.inputStream().use { load(it) } }
            .entries.mapNotNull { (k, v) -> runCatching { ModelKind.valueOf(k.toString()) to v.toString() }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())

    /** Makes [id] the active model of its slot. */
    fun activate(id: String) {
        val m = _models.value.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Model $id is not installed")
        val props = java.util.Properties()
        active().forEach { (k, v) -> props[k.name] = v }
        props[m.kind.name] = id
        root.mkdirs()
        activeFile.outputStream().use { props.store(it, "Active model per slot") }
        refresh()
    }

    /** Re-reads what is installed (after a download, import or delete). */
    fun refresh() {
        _models.value = scanAll()
        _installed.value = scan()
    }

    private fun scanAll(): List<InstalledModel> {
        val imported = ModelKind.entries.mapNotNull { kind ->
            val d = dir(kind)
            val entry = if (kind.isDirectory) d.takeIf { it.isDirectory && !it.list().isNullOrEmpty() }
            else d.listFiles()?.firstOrNull { it.isFile && !it.name.endsWith(".part") }
            entry?.let {
                val integrity = runCatching { File(File(root, ".hashes"), kind.name).readLines() }.getOrNull()
                val verified = integrity?.getOrNull(2)?.let { sha -> OfficialChecksums.verdict(it.name, sha) == OfficialChecksums.Verdict.MATCHES_OFFICIAL } == true
                InstalledModel("import:${kind.name}", kind, "Imported: ${it.name}", it, d, sizeOf(d), null, verified)
            }
        }
        val fromCatalog = catalogRoot.listFiles()?.filter { it.isDirectory && File(it, ".complete").isFile }?.mapNotNull { d ->
            val m = Catalog.byId(d.name) ?: return@mapNotNull null
            val kind = ModelKind.of(m.category)
            val main = if (kind.isDirectory) d else File(d, m.files.first().fileName)
            InstalledModel(m.id, kind, m.name, main, d, sizeOf(d), m, verified = true)
        }.orEmpty()
        return fromCatalog + imported
    }

    // _models is declared before _installed, so it is initialised when scan() first runs.
    private fun scan(): Map<ModelKind, File> {
        val all = _models.value
        val act = active()
        return ModelKind.entries.mapNotNull { kind ->
            val chosen = act[kind]?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull { it.kind == kind }
            chosen?.let { kind to it.main }
        }.toMap()
    }

    /**
     * Deletes a model. The active model of a required slot (speech, language model) is only deleted when another
     * model can take over, which then becomes active.
     */
    fun delete(id: String) {
        val m = _models.value.firstOrNull { it.id == id } ?: return
        val others = _models.value.filter { it.kind == m.kind && it.id != id }
        if (m.kind.required && activeId(m.kind) == id && others.isEmpty()) {
            throw IllegalStateException("${m.name} is the only ${m.kind.category.label.lowercase()} model. Download or import another before deleting it.")
        }
        m.dir.deleteRecursively()
        if (m.catalog == null) File(File(root, ".hashes"), m.kind.name).delete()
        if (activeId(m.kind) == id || active()[m.kind] == id) others.firstOrNull()?.let { activate(it.id) }
        if (m.kind == ModelKind.TTS && _models.value.none { it.kind == ModelKind.TTS && it.id != id }) espeakDir.parentFile?.deleteRecursively()
        _integrity.value = loadIntegrity()
        refresh()
    }

    /** Bytes used by models (total) and free space on the internal storage volume. */
    fun usedBytes(): Long = sizeOf(root)
    fun freeBytes(): Long = root.apply { mkdirs() }.usableSpace

    fun sizeOf(f: File): Long = if (f.isDirectory) f.listFiles()?.sumOf { sizeOf(it) } ?: 0 else if (f.exists() && !java.nio.file.Files.isSymbolicLink(f.toPath())) f.length() else 0

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
                val match = OfficialChecksums.match(name, copySha)
                if (match.verdict == OfficialChecksums.Verdict.DIFFERS_FROM_OFFICIAL) {
                    val official = OfficialChecksums.known.getValue(requireNotNull(match.officialName))
                    throw IllegalArgumentException(
                        "$name does not match the official ${match.officialName} (SHA-256 ${copySha.take(12)}…, " +
                            "expected ${official.sha256.take(12)}…). The download is damaged; download it again.",
                    )
                }
                saveIntegrity(kind, ModelIntegrity(name, file.length(), copySha, match.verdict, match.officialName))
            }
            target.deleteRecursively()
            check(staging.renameTo(target)) { "Could not move model into place" }
            // An imported file that is a catalogue model (same SHA-256) is filed as that verified model and made
            // active; any other import becomes the active model of its slot.
            refresh()
            if (!adoptIfCatalog(kind)) activate("import:${kind.name}")
        } finally {
            staging.deleteRecursively()
            _progress.value = null
            refresh()
        }
    }

    fun remove(kind: ModelKind) {
        dir(kind).deleteRecursively()
        File(hashDir, kind.name).delete()
        _integrity.value = loadIntegrity()
        refresh()
    }

    /** Hashes the installed file and compares it with the official checksum (for models imported earlier). */
    suspend fun verify(kind: ModelKind): ModelIntegrity = withContext(Dispatchers.IO) {
        val file = requireNotNull(file(kind)?.takeIf { it.isFile }) { "${kind.label} is not installed" }
        try {
            val sha = sha256(file) { n -> _progress.value = ImportProgress(kind, n, file.length(), verifying = true) }
            val match = OfficialChecksums.match(file.name, sha)
            ModelIntegrity(file.name, file.length(), sha, match.verdict, match.officialName).also { saveIntegrity(kind, it) }
        } finally {
            _progress.value = null
        }
    }

    /** Moves an imported file into the catalogue layout when its hash matches a catalogue file. */
    private fun adoptIfCatalog(kind: ModelKind): Boolean {
        if (kind.isDirectory) return false
        val sha = runCatching { File(hashDir, kind.name).readLines()[2] }.getOrNull() ?: return false
        val m = Catalog.models.firstOrNull { cm -> cm.files.size == 1 && cm.files[0].hash.equals(sha, true) } ?: return false
        val src = dir(kind).listFiles()?.firstOrNull { it.isFile } ?: return false
        val dest = catalogDir(m.id).apply { deleteRecursively(); mkdirs() }
        check(src.renameTo(File(dest, m.files[0].fileName))) { "Could not file the imported model" }
        File(dest, ".complete").writeText(sha)
        dir(kind).deleteRecursively()
        File(hashDir, kind.name).delete()
        _integrity.value = loadIntegrity()
        refresh()
        activate(m.id)
        return true
    }

    private fun saveIntegrity(kind: ModelKind, i: ModelIntegrity) {
        hashDir.mkdirs()
        File(hashDir, kind.name).writeText("${i.fileName}\n${i.sizeBytes}\n${i.sha256}\n${i.verdict.name}\n${i.officialName.orEmpty()}")
        _integrity.value = loadIntegrity()
    }

    private fun loadIntegrity(): Map<ModelKind, ModelIntegrity> = ModelKind.entries.mapNotNull { kind ->
        val lines = File(hashDir, kind.name).takeIf { it.isFile }?.readLines() ?: return@mapNotNull null
        runCatching {
            // Re-evaluate with the current matching rules, so results saved by older versions are corrected.
            val match = OfficialChecksums.match(lines[0], lines[2])
            kind to ModelIntegrity(lines[0], lines[1].toLong(), lines[2], match.verdict, match.officialName)
        }.getOrNull()
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
