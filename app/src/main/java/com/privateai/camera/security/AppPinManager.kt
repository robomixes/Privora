// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.content.Context
import android.util.Log
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores and verifies the app unlock PIN.
 *
 * Storage is PBKDF2-hashed (same parameters as DuressManager) — never plaintext.
 *
 * Migration: previous builds wrote the PIN as plain text in `app_pin`. The first
 * successful verify() against the legacy plain value re-hashes it and removes the
 * plaintext entry. No version flag, no separate migration job.
 */
object AppPinManager {

    private const val TAG = "AppPinManager"
    private const val PREFS_NAME = "privateai_prefs"

    // Legacy plaintext key (older builds). Removed transparently on first successful verify().
    private const val KEY_LEGACY_PIN = "app_pin"
    // Hashed format keys.
    private const val KEY_PIN_HASH = "app_pin_hash"
    private const val KEY_PIN_SALT = "app_pin_salt"

    private const val HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HASH_ITERATIONS = 10_000
    private const val HASH_KEY_LENGTH = 256
    private const val SALT_SIZE = 16

    fun isSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PIN_HASH) || prefs.contains(KEY_LEGACY_PIN)
    }

    /**
     * Set (or replace) the app PIN. Always writes the hashed format and removes
     * any leftover plaintext entry.
     */
    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PIN_HASH, hash.toHex())
            .putString(KEY_PIN_SALT, salt.toHex())
            .remove(KEY_LEGACY_PIN)
            .apply()

        Log.i(TAG, "App PIN set (hashed)")
    }

    /**
     * Verify a candidate PIN. Prefers the hashed format. If only the legacy
     * plaintext entry exists and it matches, transparently re-hash it and remove
     * the plaintext key (lazy migration).
     */
    fun verify(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val storedHashHex = prefs.getString(KEY_PIN_HASH, null)
        val storedSaltHex = prefs.getString(KEY_PIN_SALT, null)
        if (storedHashHex != null && storedSaltHex != null) {
            val salt = storedSaltHex.hexToBytes()
            val candidate = hashPin(pin, salt)
            return candidate.contentEquals(storedHashHex.hexToBytes())
        }

        // Legacy: plaintext PIN from older builds. Verify, then migrate.
        val legacy = prefs.getString(KEY_LEGACY_PIN, null) ?: return false
        if (legacy != pin) return false

        try {
            setPin(context, pin)
            Log.i(TAG, "Migrated legacy plaintext PIN to hashed storage")
        } catch (e: Exception) {
            Log.w(TAG, "PIN migration failed (will retry on next unlock): ${e.message}")
        }
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_LEGACY_PIN)
            .apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, HASH_ITERATIONS, HASH_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(HASH_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return hash
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        }
        return data
    }
}
