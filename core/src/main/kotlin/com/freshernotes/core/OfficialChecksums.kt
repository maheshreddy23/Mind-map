package com.freshernotes.core

/** Official SHA-256 (Hugging Face LFS oid) and size of commonly used model files. */
object OfficialChecksums {
    data class Entry(val sizeBytes: Long, val sha256: String)

    val known: Map<String, Entry> = mapOf(
        "ggml-tiny.en.bin" to Entry(77_704_715, "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"),
        "ggml-base.en.bin" to Entry(147_964_211, "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002"),
        "ggml-base.bin" to Entry(147_951_465, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"),
        "ggml-small.en.bin" to Entry(487_614_201, "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d"),
        "ggml-small.bin" to Entry(487_601_967, "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"),
        "ggml-medium.en.bin" to Entry(1_533_774_781, "cc37e93478338ec7700281a7ac30a10128929eb8f427dda2e865faa8f6da4356"),
        "ggml-silero-v5.1.2.bin" to Entry(885_098, "29940d98d42b91fbd05ce489f3ecf7c72f0a42f027e4875919a28fb4c04ea2cf"),
        "ggml-silero-v6.2.0.bin" to Entry(885_098, "2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987"),
        "qwen2.5-3b-instruct-q4_k_m.gguf" to Entry(2_104_932_768, "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d"),
        "qwen2.5-1.5b-instruct-q4_k_m.gguf" to Entry(1_117_320_736, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"),
    )

    enum class Verdict { MATCHES_OFFICIAL, DIFFERS_FROM_OFFICIAL, UNKNOWN_FILE }

    /** [officialName] is the known file the hash matched, or that the (normalized) name refers to. */
    data class Match(val verdict: Verdict, val officialName: String?)

    private val byHash: Map<String, String> = known.entries.associate { it.value.sha256 to it.key }

    /**
     * Strips the suffixes that browsers and download managers add to repeated downloads
     * ("ggml-small.en (1).bin", "ggml-small.en-1.bin", "ggml-small.en_2.bin", "ggml-small.en(3).bin").
     */
    fun normalizeName(fileName: String): String {
        val lower = fileName.trim().lowercase()
        val dot = lower.lastIndexOf('.')
        if (dot <= 0) return lower
        val stem = lower.substring(0, dot).replace(Regex("""(\s*\(\d+\)|[-_]\d+)$"""), "").trimEnd()
        return stem + lower.substring(dot)
    }

    /** Matches by hash first (the name does not matter), then by normalized name. */
    fun match(fileName: String, sha256: String): Match {
        byHash[sha256.lowercase()]?.let { return Match(Verdict.MATCHES_OFFICIAL, it) }
        val name = normalizeName(fileName)
        return if (name in known) Match(Verdict.DIFFERS_FROM_OFFICIAL, name) else Match(Verdict.UNKNOWN_FILE, null)
    }

    fun verdict(fileName: String, sha256: String): Verdict = match(fileName, sha256).verdict

    /** Turns low-level loader errors into advice. */
    fun loadFailureHint(reason: String): String? = when {
        listOf("unknown tensor", "wrong size", "wrong shape", "not all tensors", "bad magic", "invalid model").any { reason.contains(it, true) } ->
            "The model file is damaged (often an interrupted download that left the right size but missing data). Use Settings → Verify, then download it again."
        else -> null
    }
}
