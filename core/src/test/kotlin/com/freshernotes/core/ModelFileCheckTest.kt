package com.freshernotes.core

import com.freshernotes.core.ModelFileCheck.Format
import com.freshernotes.core.ModelFileCheck.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFileCheckTest {
    private val ggml = byteArrayOf(0x6c, 0x6d, 0x67, 0x67, 0x98.toByte(), 0xca.toByte(), 0, 0)
    private val big = 400L shl 20

    @Test fun acceptsWhisperGgml() = assertEquals(Result.Ok, ModelFileCheck.check(Format.GGML, ggml, big, "ggml-small.en.bin"))
    @Test fun acceptsGguf() = assertEquals(Result.Ok, ModelFileCheck.check(Format.GGUF, "GGUF\u0003\u0000".toByteArray(), big, "q.gguf"))

    @Test fun rejectsGgufInWhisperSlot() {
        val r = ModelFileCheck.check(Format.GGML, "GGUF\u0003".toByteArray(), big, "x.gguf")
        assertTrue(r is Result.Bad && r.message.contains("GGUF model"))
    }

    @Test fun rejectsSavedWebPage() {
        val r = ModelFileCheck.check(Format.GGML, "<!DOCTYPE html>".toByteArray(), big, "ggml-small.en.bin")
        assertTrue(r is Result.Bad && r.message.contains("web page"))
    }

    @Test fun rejectsTinyFile() {
        val r = ModelFileCheck.check(Format.GGML, ggml, 1000, "ggml-small.en.bin")
        assertTrue(r is Result.Bad && r.message.contains("incomplete"))
    }

    @Test fun rejectsArchive() {
        val r = ModelFileCheck.check(Format.GGML, byteArrayOf(0x42, 0x5a, 0x68, 0x39), big, "voice.tar.bz2")
        assertTrue(r is Result.Bad && r.message.contains("extract"))
    }
}
