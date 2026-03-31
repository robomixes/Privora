package com.privateai.camera.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages the Key Encryption Key (KEK) in Android Keystore.
 *
 * The KEK is hardware-backed (TEE/StrongBox), per-install, non-exportable.
 * It wraps/unwraps the Data Encryption Key (DEK) which does actual file encryption.
 */
object KeyManager {

    private const val TAG = "KeyManager"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEK_ALIAS = "privateai_kek"

    /**
     * Get or create the KEK. Tries StrongBox first, falls back to TEE.
     */
    fun getOrCreateKEK(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        // Return existing key if present
        val existingKey = keyStore.getKey(KEK_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            Log.d(TAG, "KEK loaded from Keystore")
            return existingKey
        }

        // Generate new KEK
        return try {
            generateKEK(useStrongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            Log.w(TAG, "StrongBox unavailable, falling back to TEE")
            generateKEK(useStrongBox = false)
        }
    }

    private fun generateKEK(useStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (useStrongBox) {
            specBuilder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(specBuilder.build())
        val key = keyGenerator.generateKey()
        Log.i(TAG, "KEK generated (StrongBox=$useStrongBox)")
        return key
    }

    /**
     * Delete the KEK — makes all encrypted data permanently unreadable.
     * Used by duress PIN wipe.
     */
    fun deleteKEK() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        if (keyStore.containsAlias(KEK_ALIAS)) {
            keyStore.deleteEntry(KEK_ALIAS)
            Log.i(TAG, "KEK deleted from Keystore")
        }
    }

    /**
     * Check if KEK exists (vault has been initialized).
     */
    fun hasKEK(): Boolean {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        return keyStore.containsAlias(KEK_ALIAS)
    }
}
