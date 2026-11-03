package com.maheshreddy.mym.data

import android.content.Context
import android.net.Uri
import com.maheshreddy.mym.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted export/import of the whole library (Settings → Backup) to a file the user chooses.
 * Contents: the database (subjects, topics, entries, sessions, transcripts), settings and the butler profile and
 * journal. Models are not included (they can be downloaded again). Format: "MYMB", version, PBKDF2 salt and
 * AES-GCM IV, then AES-256-GCM ciphertext of a zip. The key comes from the passphrase (PBKDF2-HMAC-SHA256,
 * 210,000 iterations); without it the file cannot be read, and a wrong passphrase or a tampered file is rejected.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val dbName = "mym.db"
    private val prefsFiles = listOf("datastore/settings.preferences_pb", "datastore/butler.preferences_pb")

    suspend fun export(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        require(passphrase.size >= 6) { "Use a passphrase of at least 6 characters." }
        // Fold the write-ahead log into the main file so the copy is complete.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z ->
            z.putNextEntry(ZipEntry(dbName)); context.getDatabasePath(dbName).inputStream().use { it.copyTo(z) }; z.closeEntry()
            for (p in prefsFiles) {
                val f = File(context.filesDir, p)
                if (f.isFile) { z.putNextEntry(ZipEntry(p)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
        }
        val out = encrypt(zip.toByteArray(), passphrase)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot write the backup file" }.use { it.write(out) }
    }

    /**
     * Replaces the library with the backup. The database is closed, so the app must restart afterwards
     * ([restartRequired] is true on success).
     */
    suspend fun import(uri: Uri, passphrase: CharArray): Boolean = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read the backup file" }.use { it.readBytes() }
        val zip = decrypt(bytes, passphrase)
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                require(!e.name.contains("..")) { "Invalid backup" }
                files[e.name] = z.readBytes()
            }
        }
        val dbBytes = files[dbName] ?: throw IllegalArgumentException("This backup has no library in it.")
        require(String(dbBytes.copyOf(15)) == "SQLite format 3") { "The backup's library is damaged." }
        db.close()
        val dbFile = context.getDatabasePath(dbName)
        File(dbFile.path + "-wal").delete(); File(dbFile.path + "-shm").delete()
        dbFile.writeBytes(dbBytes)
        for (p in prefsFiles) files[p]?.let { File(context.filesDir, p).apply { parentFile?.mkdirs() }.writeBytes(it) }
        File(context.filesDir, "search.db").delete() // rebuilt from the restored library on next start
        context.getSharedPreferences("search_index", Context.MODE_PRIVATE).edit().clear().commit()
        true
    }

    companion object {
        private val MAGIC = "MYMB".toByteArray()
        private const val VERSION = 1
        private const val ITERATIONS = 210_000

        fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
            val rnd = SecureRandom()
            val salt = ByteArray(16).also(rnd::nextBytes)
            val iv = ByteArray(12).also(rnd::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv)) }
            cipher.updateAAD(MAGIC)
            val ct = cipher.doFinal(plain)
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { o -> o.write(MAGIC); o.writeInt(VERSION); o.write(salt); o.write(iv); o.write(ct) }
            return out.toByteArray()
        }

        fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
            val input = DataInputStream(ByteArrayInputStream(data))
            val magic = ByteArray(4).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC)) { "This is not a MYM backup file." }
            require(input.readInt() == VERSION) { "This backup was made by a newer version of MYM." }
            val salt = ByteArray(16).also { input.readFully(it) }
            val iv = ByteArray(12).also { input.readFully(it) }
            val ct = input.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv)) }
            cipher.updateAAD(MAGIC)
            return try {
                cipher.doFinal(ct)
            } catch (e: javax.crypto.AEADBadTagException) {
                throw IllegalArgumentException("Wrong passphrase, or the backup file was changed.")
            }
        }

        private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            return SecretKeySpec(bytes, "AES")
        }
    }
}
