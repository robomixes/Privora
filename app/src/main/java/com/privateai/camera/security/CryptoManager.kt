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
        val dek = cachedDEK ?: throw IllegalStateException("CryptoManager not initialized")
        val iv = encrypted.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = encrypted.copyOfRange(GCM_IV_SIZE, encrypted.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_SIZE, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypt a file: read plaintext → encrypt → write to .enc file atomically.
     */
    fun encryptFile(inputFile: File, outputFile: File) {
        val plaintext = FileInputStream(inputFile).use { it.readBytes() }
        val encrypted = encrypt(plaintext)

        // Atomic write: write to temp, then rename
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        FileOutputStream(tempFile).use { it.write(encrypted) }
        tempFile.renameTo(outputFile)
    }

    /**
     * Decrypt a file: read .enc → decrypt → return plaintext bytes.
     * Never writes decrypted data to disk.
     */
    fun decryptFile(encryptedFile: File): ByteArray {
        val encrypted = FileInputStream(encryptedFile).use { it.readBytes() }
        return decrypt(encrypted)
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
