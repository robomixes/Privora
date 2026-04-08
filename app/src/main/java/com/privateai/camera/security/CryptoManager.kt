package com.privateai.camera.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles two-layer encryption:
 * - KEK (from KeyManager/Android Keystore) wraps/unwraps the DEK
 * - DEK (AES-256) encrypts/decrypts actual photo/note data
 *
 * File format for encrypted files (.enc):
 *   [12-byte IV][16-byte GCM auth tag][ciphertext]
 *
 * DEK is stored wrapped (encrypted by KEK) in app-internal storage.
 */
class CryptoManager(private val context: Context) {

    companion object {
        private const val TAG = "CryptoManager"
        private const val DEK_FILE = "wrapped_dek.bin"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_SIZE = 128 // bits
        private const val DEK_SIZE = 32 // 256 bits
        private const val CHUNK_SIZE = 16L * 1024 * 1024 // 16MB chunks for large file encryption
        private const val CHUNKED_VERSION: Byte = 2
    }

    private var cachedDEK: SecretKey? = null

    /**
     * Initialize the vault — generate DEK if first time, or unwrap existing DEK.
     */
    fun initialize(): Boolean {
        return try {
            val kek = KeyManager.getOrCreateKEK()
            val dekFile = File(context.filesDir, DEK_FILE)

            if (dekFile.exists()) {
                // Unwrap existing DEK
                cachedDEK = unwrapDEK(kek, dekFile.readBytes())
                Log.d(TAG, "DEK unwrapped successfully")
            } else {
                // First time — generate DEK and wrap it
                val dek = generateDEK()
                val wrappedDek = wrapDEK(kek, dek)
                dekFile.writeBytes(wrappedDek)
                cachedDEK = dek
                Log.i(TAG, "New DEK generated and wrapped")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize crypto: ${e.message}")
            false
        }
    }

    /**
     * Encrypt data with DEK using AES-256-GCM.
     * Returns: [12-byte IV][ciphertext+tag]
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        if (cachedDEK == null) initialize()
        val dek = cachedDEK ?: throw IllegalStateException("CryptoManager not initialized")
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, dek)
        val iv = cipher.iv // 12 bytes, auto-generated
        val ciphertext = cipher.doFinal(plaintext)
        // Prepend IV to ciphertext
        return iv + ciphertext
    }

    /**
     * Decrypt data with DEK using AES-256-GCM.
     * Input: [12-byte IV][ciphertext+tag]
     */
    fun decrypt(encrypted: ByteArray): ByteArray {
        if (cachedDEK == null) initialize()
        val dek = cachedDEK ?: throw IllegalStateException("CryptoManager not initialized")
        val iv = encrypted.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = encrypted.copyOfRange(GCM_IV_SIZE, encrypted.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_SIZE, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypt a file using chunked GCM to avoid OOM on large files.
     * For files > 16MB: splits into 16MB chunks, each independently GCM-encrypted.
     * Format: [1-byte version=2][for each chunk: [12-byte IV][4-byte chunk size][encrypted chunk + GCM tag]]
     * For files <= 16MB: original format [12-byte IV][ciphertext+tag] (version byte absent = v1)
     */
    fun encryptFile(inputFile: File, outputFile: File) {
        if (cachedDEK == null) initialize()
        val dek = cachedDEK ?: throw IllegalStateException("CryptoManager not initialized")

        val fileSize = inputFile.length()
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")

        if (fileSize <= CHUNK_SIZE) {
            // Small file: encrypt in one shot (original format, backward compatible)
            val plaintext = FileInputStream(inputFile).use { it.readBytes() }
            val encrypted = encrypt(plaintext)
            FileOutputStream(tempFile).use { it.write(encrypted) }
        } else {
            // Large file: chunked encryption
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(tempFile).use { fos ->
                    fos.write(CHUNKED_VERSION.toInt()) // version marker
                    val buffer = ByteArray(CHUNK_SIZE.toInt())
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val cipher = Cipher.getInstance(AES_GCM)
                        cipher.init(Cipher.ENCRYPT_MODE, dek)
                        val iv = cipher.iv
                        val chunkCiphertext = cipher.doFinal(buffer, 0, bytesRead)
                        fos.write(iv) // 12 bytes
                        // Write chunk ciphertext size as 4 bytes big-endian
                        val size = chunkCiphertext.size
                        fos.write(size shr 24)
                        fos.write(size shr 16)
                        fos.write(size shr 8)
                        fos.write(size)
                        fos.write(chunkCiphertext)
                    }
                }
            }
        }
        tempFile.renameTo(outputFile)
    }

    /**
     * Decrypt a file: read .enc → decrypt → return plaintext bytes.
     * Supports both original format (small files) and chunked format (large files).
     * Never writes decrypted data to disk.
     */
    fun decryptFile(encryptedFile: File): ByteArray {
        if (cachedDEK == null) initialize()
        val dek = cachedDEK ?: throw IllegalStateException("CryptoManager not initialized")

        // Guard against OOM: reject files that would exceed available memory
        val fileSize = encryptedFile.length()
        val maxDecryptSize = 100L * 1024 * 1024 // 100MB max for in-memory decryption
        if (fileSize > maxDecryptSize) {
            throw OutOfMemoryError("File too large for in-memory decryption: ${fileSize / 1024 / 1024}MB (max ${maxDecryptSize / 1024 / 1024}MB)")
        }

        FileInputStream(encryptedFile).use { fis ->
            // Peek first byte to detect format
            val firstByte = fis.read()
            if (firstByte == CHUNKED_VERSION.toInt()) {
                // Chunked format: read chunks
                val result = java.io.ByteArrayOutputStream()
                val ivBuf = ByteArray(GCM_IV_SIZE)
                val sizeBuf = ByteArray(4)
                while (fis.read(ivBuf) == GCM_IV_SIZE) {
                    if (fis.read(sizeBuf) != 4) break
                    val chunkSize = ((sizeBuf[0].toInt() and 0xFF) shl 24) or
                            ((sizeBuf[1].toInt() and 0xFF) shl 16) or
                            ((sizeBuf[2].toInt() and 0xFF) shl 8) or
                            (sizeBuf[3].toInt() and 0xFF)
                    // Sanity check: chunk can't be larger than 16MB + GCM tag (16 bytes)
                    if (chunkSize <= 0 || chunkSize > CHUNK_SIZE + 1024) {
                        Log.e("CryptoManager", "Invalid chunk size: $chunkSize — file may be corrupt or not chunked format")
                        throw IllegalStateException("Corrupt chunked file: invalid chunk size $chunkSize")
                    }
                    val chunkData = ByteArray(chunkSize)
                    var read = 0
                    while (read < chunkSize) {
                        val n = fis.read(chunkData, read, chunkSize - read)
                        if (n <= 0) break
                        read += n
                    }
                    val cipher = Cipher.getInstance(AES_GCM)
                    cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_SIZE, ivBuf))
                    result.write(cipher.doFinal(chunkData))
                }
                return result.toByteArray()
            } else {
                // Original format: first byte is part of IV
                val ivRest = ByteArray(GCM_IV_SIZE - 1)
                fis.read(ivRest)
                val iv = ByteArray(GCM_IV_SIZE)
                iv[0] = firstByte.toByte()
                System.arraycopy(ivRest, 0, iv, 1, ivRest.size)
                val ciphertext = fis.readBytes()
                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_SIZE, iv))
                return cipher.doFinal(ciphertext)
            }
        }
    }

    /**
     * Encrypt bytes and write to file atomically.
     */
    fun encryptToFile(data: ByteArray, outputFile: File) {
        val encrypted = encrypt(data)
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        FileOutputStream(tempFile).use { it.write(encrypted) }
        tempFile.renameTo(outputFile)
    }

    /**
     * Clear cached DEK from memory.
     */
    fun lock() {
        cachedDEK = null
        Log.d(TAG, "DEK cleared from memory")
    }

    /**
     * Check if crypto is initialized and DEK is in memory.
     */
    fun isUnlocked(): Boolean = cachedDEK != null

    /**
     * Get raw DEK bytes for backup export. Only callable when unlocked.
     */
    fun getDekBytes(): ByteArray {
        val dek = cachedDEK ?: throw IllegalStateException("CryptoManager not initialized")
        return dek.encoded.copyOf()
    }

    /**
     * Import a DEK from backup and re-wrap with this device's KEK.
     * Replaces any existing DEK.
     */
    fun importDek(dekBytes: ByteArray) {
        cachedDEK = SecretKeySpec(dekBytes, "AES")
        val kek = KeyManager.getOrCreateKEK()
        val wrapped = wrapDEK(kek, cachedDEK!!)
        File(context.filesDir, DEK_FILE).writeBytes(wrapped)
        Log.i(TAG, "DEK imported and re-wrapped with device KEK")
    }

    /**
     * Wipe all encryption keys — makes all data permanently unreadable.
     * Used by duress PIN.
     */
    fun wipeAll() {
        cachedDEK = null
        File(context.filesDir, DEK_FILE).delete()
        KeyManager.deleteKEK()
        Log.i(TAG, "All keys wiped")
    }

    // --- Private helpers ---

    private fun generateDEK(): SecretKey {
        val keyBytes = ByteArray(DEK_SIZE)
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun wrapDEK(kek: SecretKey, dek: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val iv = cipher.iv
        val wrapped = cipher.doFinal(dek.encoded)
        return iv + wrapped
    }

    private fun unwrapDEK(kek: SecretKey, wrappedData: ByteArray): SecretKey {
        val iv = wrappedData.copyOfRange(0, GCM_IV_SIZE)
        val wrapped = wrappedData.copyOfRange(GCM_IV_SIZE, wrappedData.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_SIZE, iv))
        val dekBytes = cipher.doFinal(wrapped)
        return SecretKeySpec(dekBytes, "AES")
    }
}
