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
    @Test fun hintForCorruptTensor() = assertNotNull(OfficialChecksums.loadFailureHint("whisper_model_load: unknown tensor '' in model file"))
    @Test fun noHintForOtherErrors() = assertNull(OfficialChecksums.loadFailureHint("failed to allocate the compute buffer"))
}
