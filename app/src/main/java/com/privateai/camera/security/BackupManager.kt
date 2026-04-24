package com.privateai.camera.security

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles encrypted backup export/import using DEK key export approach.
 *
 * Backup is a ZIP file (.paicbackup) containing:
 *   __key__        - Wrapped DEK (~118 bytes)
 *   vault/...      - All .enc files as-is (no re-encryption)
 *
 * Key file format (__key__):
 *   [4 bytes]  Magic: "PAIK"
 *   [2 bytes]  Version: 1
 *   [16 bytes] Salt (for PBKDF2)
 *   [4 bytes]  KDF iterations (600000)
 *   [12 bytes] IV (AES-GCM)
 *   [48 bytes] Wrapped DEK (32 bytes + 16-byte GCM tag)
 *   [32 bytes] HMAC-SHA256 integrity
 */
class BackupManager(private val context: Context, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "BackupManager"
        private val KEY_MAGIC = byteArrayOf('P'.code.toByte(), 'A'.code.toByte(), 'I'.code.toByte(), 'K'.code.toByte())
        private const val KEY_VERSION: Short = 1
        private const val KEY_ENTRY = "__key__"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private const val HMAC_ALGO = "HmacSHA256"
        private const val KDF_ITERATIONS = 600_000
    }

    data class BackupStats(
        val photoCount: Int,
        val videoCount: Int,
        val noteCount: Int,
        val totalSizeBytes: Long
    )

    /** SharedPreferences to include in backup (user preferences, not security keys). */
    private val prefsToBackup = listOf(
        "unit_converter",       // last conversion settings
        "feature_toggles",      // feature order + on/off
        "app_settings",         // language preference
        "privacy_settings",     // grace period, etc.
        "qr_history",           // QR scan/generate history
    )
    private val prefsDirInZip = "__prefs__/"

    /**
     * Count items that would be included in a backup.
     */
    fun getBackupStats(): BackupStats {
        val vault = VaultRepository(context, crypto)
        val noteRepo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)

        var photos = 0
        var videos = 0
        VaultCategory.entries.forEach { cat ->
            vault.listPhotos(cat).forEach { item ->
                if (item.mediaType == VaultMediaType.VIDEO) videos++ else photos++
            }
        }
        val notes = noteRepo.noteCount()

        val vaultDir = File(context.filesDir, "vault")
        var totalSize = 0L
        vaultDir.walkTopDown().filter { it.isFile }.forEach { totalSize += it.length() }

        return BackupStats(photos, videos, notes, totalSize)
    }

    /**
     * Export backup: wrap DEK with password, zip all .enc files as-is.
     * Returns the .paicbackup file.
     */
    fun exportBackup(
        password: String,
        onProgress: (current: Int, total: Int, label: String) -> Unit
    ): File {
        // 1. Wrap DEK with password-derived key
        val salt = BackupKeyDerivation.generateSalt()
        val backupKey = BackupKeyDerivation.deriveKey(password, salt)
        val dekBytes = crypto.getDekBytes()
        val wrappedKeyData = createKeyEntry(salt, backupKey, dekBytes)
        dekBytes.fill(0) // zero plaintext DEK

        // 2. Collect all vault files
        val vaultDir = File(context.filesDir, "vault")
        val allFiles = vaultDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".enc") }
            .toList()
        val totalFiles = allFiles.size

        // 3. Create zip
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
        val backupFile = File(context.cacheDir, "privora_backup_${dateStr}.paicbackup")
        val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile)))

        // Write key entry first
        zos.putNextEntry(ZipEntry(KEY_ENTRY))
        zos.write(wrappedKeyData)
        zos.closeEntry()

        // Write vault files preserving directory structure
        allFiles.forEachIndexed { index, file ->
            val relativePath = file.relativeTo(context.filesDir).path.replace('\\', '/')
            zos.putNextEntry(ZipEntry(relativePath))
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
            onProgress(index + 1, totalFiles, "Backing up files...")
        }

        // Write SQLCipher database (contacts + photo_index) if it exists
        val dbFile = context.getDatabasePath("privora.db")
        if (dbFile.exists()) {
            // Close the database before copying to avoid corruption
            try { PrivoraDatabase.closeInstance() } catch (_: Exception) {}
            zos.putNextEntry(ZipEntry("__database__/privora.db"))
            FileInputStream(dbFile).use { fis -> fis.copyTo(zos) }
            zos.closeEntry()
            Log.i(TAG, "Database backed up: ${dbFile.length() / 1024}KB")
        }

        // Write SharedPreferences as JSON entries
        prefsToBackup.forEach { prefName ->
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            if (allEntries.isNotEmpty()) {
                val json = org.json.JSONObject()
                allEntries.forEach { (key, value) ->
                    when (value) {
                        is String -> json.put(key, value)
                        is Int -> json.put(key, value)
                        is Long -> json.put(key, value)
                        is Float -> json.put(key, value.toDouble())
                        is Boolean -> json.put(key, value)
                    }
                }
                zos.putNextEntry(ZipEntry("$prefsDirInZip$prefName.json"))
                zos.write(json.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        zos.close()

        Log.i(TAG, "Backup exported: ${backupFile.length() / 1024}KB, $totalFiles files")
        return backupFile
    }

    /**
     * Import from a .paicbackup file.
     * If the device has existing data, re-encrypts it with the imported DEK
     * so both old and new data use the same key.
     * Returns (imported count, skipped count).
     */
    fun importBackup(
        backupFile: File,
        password: String,
        onProgress: (current: Int, total: Int, label: String) -> Unit
    ): Pair<Int, Int> {
        // PASS 1: Read only the key entry and count files (small memory footprint)
        var keyData: ByteArray? = null
        var totalEntries = 0
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == KEY_ENTRY) {
                    keyData = zis.readBytes()
                } else {
                    totalEntries++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        requireNotNull(keyData) { "Invalid backup: no key found" }

        // 2. Unwrap backup DEK
        val backupDekBytes = readKeyEntry(keyData!!, password)

        // 3. Handle existing data re-encryption
        val vaultDir = File(context.filesDir, "vault")
        val existingFiles = if (vaultDir.exists()) {
            vaultDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".enc") && !it.name.startsWith("_tobedeleted_") }
                .toList()
        } else emptyList()

        val hasExistingData = existingFiles.isNotEmpty() && crypto.isUnlocked()

        if (hasExistingData) {
            // Re-encrypt existing files one at a time (no bulk memory allocation)
            Log.i(TAG, "Re-encrypting ${existingFiles.size} existing files with imported key...")
            val oldDecrypted = mutableListOf<Pair<File, ByteArray>>()

            existingFiles.forEachIndexed { index, file ->
                try {
                    val plaintext = crypto.decryptFile(file)
                    oldDecrypted.add(file to plaintext)
                } catch (e: Exception) {
                    Log.w(TAG, "Skip re-encrypt (decrypt failed): ${file.name}: ${e.message}")
                }
                onProgress(index + 1, existingFiles.size, "Re-encrypting existing data...")
            }

            crypto.importDek(backupDekBytes)
            backupDekBytes.fill(0)

            oldDecrypted.forEachIndexed { index, (file, plaintext) ->
                try {
                    crypto.encryptToFile(plaintext, file)
                } catch (e: Exception) {
                    Log.e(TAG, "Re-encrypt failed: ${file.name}: ${e.message}")
                }
                plaintext.fill(0)
                onProgress(index + 1, oldDecrypted.size, "Securing existing data...")
            }
            Log.i(TAG, "Re-encrypted ${oldDecrypted.size} existing files")
        } else {
            crypto.importDek(backupDekBytes)
            backupDekBytes.fill(0)
        }

        // PASS 2: Stream files directly to disk (no bulk memory — handles 380MB+ backups)
        var imported = 0
        var skipped = 0
        var fileIndex = 0

        ZipInputStream(BufferedInputStream(FileInputStream(backupFile), 8192)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == KEY_ENTRY) {
                    // Already processed in pass 1 — skip
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                fileIndex++

                if (entry.name.startsWith("__database__/")) {
                    // SQLCipher database (contacts + photo_index) — stream to databases dir
                    try {
                        val dbName = entry.name.removePrefix("__database__/")
                        val dbFile = context.getDatabasePath(dbName)
                        // Close existing DB before overwriting
                        try { PrivoraDatabase.closeInstance() } catch (_: Exception) {}
                        dbFile.parentFile?.mkdirs()
                        dbFile.outputStream().use { out -> zis.copyTo(out, bufferSize = 8192) }
                        Log.i(TAG, "Database restored: $dbName (${dbFile.length() / 1024}KB)")
                        imported++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore database: ${entry.name}: ${e.message}")
                        skipped++
                    }
                } else if (entry.name.startsWith(prefsDirInZip)) {
                    // SharedPreferences — small JSON, safe to read into memory
                    try {
                        val data = zis.readBytes()
                        val prefName = entry.name.removePrefix(prefsDirInZip).removeSuffix(".json")
                        val json = org.json.JSONObject(String(data, Charsets.UTF_8))
                        val editor = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit()
                        json.keys().forEach { key ->
                            when (val v = json.get(key)) {
                                is String -> editor.putString(key, v)
                                is Int -> editor.putInt(key, v)
                                is Long -> editor.putLong(key, v)
                                is Double -> editor.putFloat(key, v.toFloat())
                                is Boolean -> editor.putBoolean(key, v)
                            }
                        }
                        editor.apply()
                        Log.d(TAG, "Restored preferences: $prefName")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore prefs: ${entry.name}: ${e.message}")
                    }
                } else {
                    // Vault file — stream directly to disk (no readBytes into memory)
                    val targetFile = File(context.filesDir, entry.name)
                    if (targetFile.exists()) {
                        skipped++
                    } else {
                        try {
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { out ->
                                zis.copyTo(out, bufferSize = 8192)
                            }
                            imported++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to extract: ${entry.name}: ${e.message}")
                            skipped++
                        }
                    }
                }

                onProgress(fileIndex, totalEntries, "Restoring files...")
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        Log.i(TAG, "Backup imported: $imported files, $skipped skipped" +
            if (hasExistingData) ", ${existingFiles.size} re-encrypted" else "")
        return imported to skipped
    }

    /**
     * Create the __key__ binary data.
     */
    private fun createKeyEntry(salt: ByteArray, backupKey: SecretKey, dekBytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()

        // Header
        bos.write(KEY_MAGIC)
        bos.write(byteArrayOf((KEY_VERSION.toInt() shr 8).toByte(), KEY_VERSION.toByte()))
        bos.write(salt)
        val iterBytes = ByteArray(4)
        iterBytes[0] = (KDF_ITERATIONS shr 24).toByte()
        iterBytes[1] = (KDF_ITERATIONS shr 16).toByte()
        iterBytes[2] = (KDF_ITERATIONS shr 8).toByte()
        iterBytes[3] = KDF_ITERATIONS.toByte()
        bos.write(iterBytes)

        // Wrap DEK with backup key
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, backupKey)
        val iv = cipher.iv
        val wrappedDek = cipher.doFinal(dekBytes)
        bos.write(iv)           // 12 bytes
        bos.write(wrappedDek)   // 32 + 16 = 48 bytes

        // HMAC over everything so far
        val dataBeforeHmac = bos.toByteArray()
        val hmac = Mac.getInstance(HMAC_ALGO)
        hmac.init(SecretKeySpec(backupKey.encoded, HMAC_ALGO))
        val hmacResult = hmac.doFinal(dataBeforeHmac)
        bos.write(hmacResult)   // 32 bytes

        return bos.toByteArray()
    }

    /**
     * Read __key__ entry and unwrap DEK. Throws on wrong password or corruption.
     */
    private fun readKeyEntry(data: ByteArray, password: String): ByteArray {
        val bis = ByteArrayInputStream(data)

        // Verify magic
        val magic = ByteArray(4)
        bis.read(magic)
        require(magic.contentEquals(KEY_MAGIC)) { "Not a valid backup key" }

        // Version
        val versionHigh = bis.read()
        val versionLow = bis.read()
        val version = ((versionHigh shl 8) or versionLow).toShort()
        require(version == KEY_VERSION) { "Unsupported backup version: $version" }

        // Salt
        val salt = ByteArray(BackupKeyDerivation.SALT_SIZE)
        bis.read(salt)

        // Iterations
        val iterBytes = ByteArray(4)
        bis.read(iterBytes)

        // Derive key
        val backupKey = BackupKeyDerivation.deriveKey(password, salt)

        // IV + wrapped DEK
        val iv = ByteArray(GCM_IV_SIZE)
        bis.read(iv)
        val wrappedDek = ByteArray(48) // 32-byte DEK + 16-byte GCM tag
        bis.read(wrappedDek)

        // Verify HMAC
        val storedHmac = ByteArray(32)
        bis.read(storedHmac)

        val dataForHmac = data.copyOfRange(0, data.size - 32)
        val hmac = Mac.getInstance(HMAC_ALGO)
        hmac.init(SecretKeySpec(backupKey.encoded, HMAC_ALGO))
        val computedHmac = hmac.doFinal(dataForHmac)
        require(computedHmac.contentEquals(storedHmac)) { "Wrong password or corrupted backup" }

        // Unwrap DEK
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(wrappedDek)
    }
}
