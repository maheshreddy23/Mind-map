package com.maheshreddy.mym.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {
    private val oneplus13r = DeviceProfile.of(totalRamMb = 11_700, socModel = "SM8650", cores = 8)
    private val budget6gb = DeviceProfile.of(totalRamMb = 5_800, socModel = "MT6789", cores = 8)

    @Test
    fun everyEntryIsPinnedAndHashed() {
        val ids = Catalog.models.map { it.id }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
        for (m in Catalog.models) {
            assertTrue(m.id, Regex("^[0-9a-f]{40}$").matches(m.revision))
            assertTrue(m.id, m.files.isNotEmpty())
            for (f in m.files) {
                assertTrue("${m.id} ${f.path}", f.size > 0)
                val len = if (f.hashKind == HashKind.SHA256) 64 else 40
                assertTrue("${m.id} ${f.path} hash", Regex("^[0-9a-f]{$len}$").matches(f.hash))
                assertTrue(f.url(m.repo, m.revision).startsWith("https://huggingface.co/${m.repo}/resolve/${m.revision}/"))
            }
        }
        for (c in ModelCategory.entries) assertTrue("$c has models", Catalog.inCategory(c).isNotEmpty())
        assertTrue(Catalog.inCategory(ModelCategory.VOICE).all { it.sharedData == SharedData.ESPEAK_NG })
        assertTrue(Catalog.inCategory(ModelCategory.EMBEDDING).all { (it.embeddingDim ?: 0) > 0 })
    }

    @Test
    fun deviceCheckClassifiesChips() {
        assertEquals(SocTier.FLAGSHIP, oneplus13r.tier)
        assertEquals(12, oneplus13r.ramGb)
        assertEquals(SocTier.MID, budget6gb.tier)
        assertEquals(SocTier.UPPER, DeviceProfile.of(12_000, null, 8).tier)
        assertEquals(SocTier.LOW, DeviceProfile.of(3_800, "unknown", 8).tier)
    }

    @Test
    fun recommendationsFitThePhoneAndAreOpenSource() {
        val rec = DeviceFit.recommended(oneplus13r)
        assertEquals("whisper-small-en", rec[ModelCategory.SPEECH]?.id)
        assertEquals("qwen3-4b-instruct-2507", rec[ModelCategory.LLM]?.id)
        assertEquals("bge-small-en", rec[ModelCategory.EMBEDDING]?.id)
        assertNotNull(rec[ModelCategory.VOICE])
        assertTrue(rec.values.all { it.licence.openSource })
        val small = DeviceFit.recommended(budget6gb)
        assertEquals("qwen2.5-1.5b", small[ModelCategory.LLM]?.id)
        assertEquals("whisper-base-en", small[ModelCategory.SPEECH]?.id)
        assertEquals("whisper-small", DeviceFit.recommended(oneplus13r, multilingual = true)[ModelCategory.SPEECH]?.id)
    }

    @Test
    fun estimatesMatchTheMeasuredBaselines() {
        val qwen25 = Catalog.byId("qwen2.5-3b")!!
        assertEquals(12.4, DeviceFit.fit(qwen25, oneplus13r).speedScore, 0.01)
        val small = DeviceFit.fit(Catalog.byId("whisper-small-en")!!, oneplus13r)
        assertEquals("2.1× faster than real time", small.speed)
        assertFalse("8 GB of Gemma 4 E4B does not fit a 6 GB phone", DeviceFit.fit(Catalog.byId("gemma4-e4b")!!, budget6gb).fits)
        val bySize = DeviceFit.sorted(Catalog.inCategory(ModelCategory.LLM), oneplus13r, DeviceFit.Sort.SIZE, emptySet())
        assertTrue(bySize.zipWithNext().all { (a, b) -> a.sizeBytes <= b.sizeBytes })
    }

    @Test
    fun checksumsMatchKnownValues() {
        val f = File.createTempFile("mym", ".txt").apply { writeText("hello\n"); deleteOnExit() }
        // `printf 'hello\n' | sha256sum` and `git hash-object` of the same content.
        assertEquals("5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03", Checksums.hash(f, HashKind.SHA256))
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", Checksums.hash(f, HashKind.GIT_SHA1))
        assertTrue(Checksums.verify(f, RemoteFile("x", 6, "ce013625030ba8dba906f756967f9e9ca394464a", HashKind.GIT_SHA1)))
        assertFalse("wrong size fails", Checksums.verify(f, RemoteFile("x", 7, "ce013625030ba8dba906f756967f9e9ca394464a", HashKind.GIT_SHA1)))
        assertFalse("wrong hash fails", Checksums.verify(f, RemoteFile("x", 6, "0".repeat(64))))
    }

    @Test
    fun espeakManifestParses() {
        val files = Catalog.parseManifest("espeak-ng-data/en_dict\t167165\tabc123\nespeak-ng-data/phontab\t55\tdef456\n")
        assertEquals(2, files.size)
        assertEquals(HashKind.GIT_SHA1, files[0].hashKind)
        assertEquals(167165L, files[0].size)
    }
}
