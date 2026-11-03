package com.maheshreddy.mym

import com.maheshreddy.mym.data.BackupManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val plain = "SQLite format 3\u0000 my library".toByteArray()

    @Test
    fun roundTripsWithTheRightPassphrase() {
        val enc = BackupManager.encrypt(plain, "correct horse".toCharArray())
        assertFalse("ciphertext does not contain the plain text", String(enc, Charsets.ISO_8859_1).contains("my library"))
        assertArrayEquals(plain, BackupManager.decrypt(enc, "correct horse".toCharArray()))
    }

    @Test
    fun rejectsAWrongPassphraseOrATamperedFile() {
        val enc = BackupManager.encrypt(plain, "correct horse".toCharArray())
        assertTrue(runCatching { BackupManager.decrypt(enc, "wrong horse".toCharArray()) }.exceptionOrNull()?.message!!.contains("Wrong passphrase"))
        val tampered = enc.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertTrue(runCatching { BackupManager.decrypt(tampered, "correct horse".toCharArray()) }.isFailure)
        assertTrue(runCatching { BackupManager.decrypt("not a backup".toByteArray(), "x".toCharArray()) }.exceptionOrNull()?.message!!.contains("not a MYM backup"))
    }
}
