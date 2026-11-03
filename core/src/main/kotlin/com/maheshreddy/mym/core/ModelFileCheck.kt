package com.maheshreddy.mym.core

/** Cheap header checks so a wrong or broken file is rejected when it is picked, not at session start. */
object ModelFileCheck {
    enum class Format { GGML, GGUF, ONNX_FOLDER }

    sealed interface Result {
        data object Ok : Result
        data class Bad(val message: String) : Result
    }

    private val GGML = byteArrayOf(0x6c, 0x6d, 0x67, 0x67) // "lmgg" = GGML_FILE_MAGIC little-endian
    private val GGUF = "GGUF".toByteArray()

    fun check(expected: Format, header: ByteArray, sizeBytes: Long, fileName: String): Result {
        if (expected == Format.ONNX_FOLDER) return Result.Ok
        val head = header.copyOf(minOf(header.size, 16))
        val actual = when {
            head.startsWith(GGML) -> "a ggml model (whisper/Silero format)"
            head.startsWith(GGUF) -> "a GGUF model (llama.cpp format)"
            String(head, Charsets.ISO_8859_1).trimStart().let { it.startsWith("<") || it.startsWith("{") } ->
                "a web page or JSON, not a model (the download probably failed or saved the page instead)"
            head.startsWith(byteArrayOf(0x50, 0x4b)) -> "a zip archive (extract it first)"
            head.size >= 3 && head[0] == 0x42.toByte() && head[1] == 0x5a.toByte() && head[2] == 0x68.toByte() -> "a .tar.bz2 archive (extract it first)"
            head.isEmpty() -> "empty"
            else -> "an unknown format"
        }
        val ok = when (expected) {
            Format.GGML -> head.startsWith(GGML)
            Format.GGUF -> head.startsWith(GGUF)
            Format.ONNX_FOLDER -> true
        }
        if (!ok) {
            val want = if (expected == Format.GGML) "a whisper.cpp ggml .bin model" else "a .gguf language model"
            return Result.Bad("$fileName is $actual; expected $want.")
        }
        if (sizeBytes < 512 * 1024) return Result.Bad("$fileName is only ${sizeBytes / 1024} KB; the download looks incomplete.")
        return Result.Ok
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
