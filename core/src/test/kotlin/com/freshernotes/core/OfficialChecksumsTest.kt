package com.freshernotes.core

import com.freshernotes.core.OfficialChecksums.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialChecksumsTest {
    @Test fun matches() = assertEquals(
        Verdict.MATCHES_OFFICIAL,
        OfficialChecksums.verdict("ggml-small.en.bin", "C6138D6D58ECC8322097E0F987C32F1BE8BB0A18532A3F88F734D1BBF9C41E5D"),
    )
    @Test fun differs() = assertEquals(Verdict.DIFFERS_FROM_OFFICIAL, OfficialChecksums.verdict("ggml-small.en.bin", "00".repeat(32)))
    @Test fun unknown() = assertEquals(Verdict.UNKNOWN_FILE, OfficialChecksums.verdict("my-model.bin", "00".repeat(32)))
    private val smallEn = "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d"

    @Test fun matchesByHashRegardlessOfName() {
        val m = OfficialChecksums.match("ggml-small.en (1).bin", smallEn)
        assertEquals(Verdict.MATCHES_OFFICIAL, m.verdict)
        assertEquals("ggml-small.en.bin", m.officialName)
        assertEquals(Verdict.MATCHES_OFFICIAL, OfficialChecksums.verdict("whatever.bin", smallEn.uppercase()))
    }

    @Test fun duplicateDownloadNameWithWrongHashIsFlaggedDamaged() {
        val m = OfficialChecksums.match("ggml-small.en (1).bin", "00".repeat(32))
        assertEquals(Verdict.DIFFERS_FROM_OFFICIAL, m.verdict)
        assertEquals("ggml-small.en.bin", m.officialName)
    }

    @Test fun normalizesCopySuffixes() {
        listOf("ggml-small.en (1).bin", "ggml-small.en(12).bin", "GGML-small.en-1.bin", "ggml-small.en_2.bin", " ggml-small.en.bin ")
            .forEach { assertEquals(it, "ggml-small.en.bin", OfficialChecksums.normalizeName(it)) }
        // Real version numbers inside the name are kept.
        assertEquals("ggml-silero-v5.1.2.bin", OfficialChecksums.normalizeName("ggml-silero-v5.1.2.bin"))
        assertEquals("qwen2.5-3b-instruct-q4_k_m.gguf", OfficialChecksums.normalizeName("qwen2.5-3b-instruct-q4_k_m (2).gguf"))
    }

    @Test fun hintForCorruptTensor() = assertNotNull(OfficialChecksums.loadFailureHint("whisper_model_load: unknown tensor '' in model file"))
    @Test fun noHintForOtherErrors() = assertNull(OfficialChecksums.loadFailureHint("failed to allocate the compute buffer"))
}
