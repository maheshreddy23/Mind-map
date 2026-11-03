package com.maheshreddy.mym.core.models

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

enum class ModelCategory(val label: String) {
    SPEECH("Speech-to-text"),
    LLM("Language model"),
    EMBEDDING("Embeddings"),
    VOICE("Voice"),
    VAD("Voice detection"),
}

/** How a file's official hash is published: SHA-256 for Git LFS files, the Git blob SHA-1 for small text files. */
enum class HashKind { SHA256, GIT_SHA1 }

/** Data some models share and that is downloaded once (Piper voices all need espeak-ng-data). */
enum class SharedData { ESPEAK_NG }

data class RemoteFile(val path: String, val size: Long, val hash: String, val hashKind: HashKind = HashKind.SHA256) {
    val fileName: String get() = path.substringAfterLast('/')
    fun url(repo: String, revision: String) = "https://huggingface.co/$repo/resolve/$revision/" +
        path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}

data class Licence(val name: String, val url: String?, val openSource: Boolean)

/** One downloadable model. Everything here was read from Hugging Face; see [CatalogData]. */
data class CatalogModel(
    val id: String,
    val category: ModelCategory,
    val name: String,
    val repo: String,
    /** Git commit the files are pinned to. */
    val revision: String,
    val files: List<RemoteFile>,
    val licence: Licence,
    /** Parameters doing work per token, in billions (drives the speed estimate). */
    val activeParamsB: Double,
    /** 1 (basic) .. 5 (best in this list). */
    val quality: Int,
    val languages: String,
    val note: String = "",
    val embeddingDim: Int? = null,
    val sharedData: SharedData? = null,
) {
    val sizeBytes: Long get() = files.sumOf { it.size }
    val multilingual: Boolean get() = languages.startsWith("Multi")

    /** Memory needed while running: weights plus working buffers (KV cache, activations, audio). */
    val ramNeededMb: Int get() {
        val mb = sizeBytes / 1_048_576.0
        return when (category) {
            ModelCategory.LLM -> (mb * 1.15 + 600).toInt()
            ModelCategory.SPEECH -> (mb * 1.3 + 150).toInt()
            ModelCategory.EMBEDDING -> (mb * 1.2 + 80).toInt()
            ModelCategory.VOICE -> (mb * 1.5 + 60).toInt()
            ModelCategory.VAD -> 20
        }
    }
}

object Catalog {
    val models: List<CatalogModel> get() = CatalogData.models
    fun byId(id: String): CatalogModel? = models.firstOrNull { it.id == id }
    fun inCategory(c: ModelCategory) = models.filter { it.category == c }
    const val ESPEAK_REPO = CatalogData.ESPEAK_REPO
    const val ESPEAK_REVISION = CatalogData.ESPEAK_REVISION

    /** Parses assets/catalog/espeak-ng-data.tsv ("path\tsize\tgit-sha1" per line). */
    fun parseManifest(tsv: String): List<RemoteFile> = tsv.lines().filter { it.isNotBlank() }.map { line ->
        val (path, size, oid) = line.split('\t')
        RemoteFile(path, size.toLong(), oid, HashKind.GIT_SHA1)
    }
}

/** Checksum verification against the official hash before a download is activated. */
object Checksums {
    fun sha256(input: InputStream): String = digest("SHA-256", input, null)

    /** Git's blob id: SHA-1 of "blob <size>\0" followed by the content (what Hugging Face lists for non-LFS files). */
    fun gitBlobSha1(input: InputStream, size: Long): String = digest("SHA-1", input, "blob $size\u0000".toByteArray())

    fun hash(file: File, kind: HashKind): String = file.inputStream().buffered().use {
        when (kind) {
            HashKind.SHA256 -> sha256(it)
            HashKind.GIT_SHA1 -> gitBlobSha1(it, file.length())
        }
    }

    fun verify(file: File, expected: RemoteFile): Boolean =
        file.length() == expected.size && hash(file, expected.hashKind).equals(expected.hash, ignoreCase = true)

    private fun digest(alg: String, input: InputStream, prefix: ByteArray?): String {
        val md = MessageDigest.getInstance(alg)
        prefix?.let { md.update(it) }
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
