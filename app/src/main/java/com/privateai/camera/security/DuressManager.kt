package com.privateai.camera.security

import android.content.Context
import android.util.Log
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class DuressMode { EMPTY_ONLY, WIPE }

/**
 * Manages the emergency/duress PIN feature.
 * PIN is stored as a PBKDF2 hash — never plaintext.
 * After duress activation, all config is erased (no trace it ever existed).
 */
object DuressManager {

    private const val TAG = "DuressManager"
    private const val PREFS_NAME = "duress_settings"
    private const val KEY_ENABLED = "duress_enabled"
    private const val KEY_PIN_HASH = "duress_pin_hash"
    private const val KEY_PIN_SALT = "duress_pin_salt"
    private const val KEY_MODE = "duress_mode"

    private const val HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HASH_ITERATIONS = 10_000 // fast enough for PIN check, strong enough for 4-8 digits
    private const val HASH_KEY_LENGTH = 256

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun getMode(context: Context): DuressMode {
        val mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, "WIPE") ?: "WIPE"
        return try { DuressMode.valueOf(mode) } catch (_: Exception) { DuressMode.WIPE }
    }

    fun setMode(context: Context, mode: DuressMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun setDuressPin(context: Context, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPin(pin, salt)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_PIN_HASH, hash.toHex())
            .putString(KEY_PIN_SALT, salt.toHex())
            .apply()

        Log.i(TAG, "Duress PIN set")
    }

    fun isDuressPin(context: Context, pin: String): Boolean {
        if (!isEnabled(context)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHash = prefs.getString(KEY_PIN_HASH, null)?.hexToBytes() ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null)?.hexToBytes() ?: return false

        val inputHash = hashPin(pin, salt)
        return inputHash.contentEquals(storedHash)
    }

    /**
     * Clear all duress config. No trace it ever existed.
     */
    fun clearDuressPin(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        // Also try to delete the prefs file itself for extra paranoia
        try {
            File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml").delete()
        } catch (_: Exception) {}
        Log.i(TAG, "Duress config erased")
    }

    private const val DELETE_PREFIX = "_tobedeleted_"

    /**
     * Execute the duress wipe sequence.
     *
     * WIPE flow (runs on IO thread):
     * 1. Delete old KEK from Keystore (instant — old data unreadable)
     * 2. Wipe old DEK from memory + disk
     * 3. Create fresh KEK + DEK (app works immediately as fresh install)
     * 4. Clear all SharedPreferences (reset to fresh state)
     * 5. Rename old vault files with _tobedeleted_ prefix (excluded from app)
     * 6. Background thread silently deletes renamed files
     *
     * Result: app is immediately usable as a fresh install. User sees empty vault/notes.
     * Old encrypted files (now unreadable) are cleaned up in background.
     */
    fun executeDuress(context: Context, crypto: CryptoManager) {
        val mode = getMode(context)

        if (mode == DuressMode.EMPTY_ONLY) {
            Log.i(TAG, "Duress: empty-only mode — showing empty, PIN preserved")
            return
        }

        // === WIPE MODE ===
        Log.i(TAG, "Duress: WIPE mode — destroying data, keeping settings")

        // 0. Save user settings that should survive the wipe
        val appPrefs = context.getSharedPreferences("privateai_prefs", Context.MODE_PRIVATE)
        val privacyPrefs = context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
        val duressPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val featurePrefs = context.getSharedPreferences("feature_toggles", Context.MODE_PRIVATE)

        // Snapshot all prefs we want to keep
        val savedApp = HashMap(appPrefs.all)
        val savedPrivacy = HashMap(privacyPrefs.all)
        val savedDuress = HashMap(duressPrefs.all)
        val savedFeatures = HashMap(featurePrefs.all)

        // 1. Delete old KEK from Android Keystore (instant — all .enc files now unreadable)
        KeyManager.deleteKEK()
        Log.i(TAG, "Duress: Old KEK deleted from Keystore")

        // 2. Wipe old DEK from memory and disk
        crypto.wipeAll()
        Log.i(TAG, "Duress: Old DEK wiped")

        // 3. Create fresh keys — app is now functional
        crypto.initialize()
        Log.i(TAG, "Duress: Fresh KEK + DEK created")

        // 4. Rename old vault files with prefix (excluded from app, deleted in background)
        val vaultDir = File(context.filesDir, "vault")
        if (vaultDir.exists()) {
            markForDeletion(vaultDir)
            Log.i(TAG, "Duress: Vault files marked for deletion")
        }

        // 5. Clear cache
        context.cacheDir.listFiles()?.forEach {
            if (it.isDirectory) it.deleteRecursively() else it.delete()
        }

        // 6. Restore saved settings (app PIN, auth mode, duress PIN, feature toggles, etc.)
        restorePrefs(appPrefs, savedApp)
        restorePrefs(privacyPrefs, savedPrivacy)
        restorePrefs(duressPrefs, savedDuress)
        restorePrefs(featurePrefs, savedFeatures)
        Log.i(TAG, "Duress: Settings restored")

        // 7. Start background deletion of marked files
        Thread {
            deleteMarkedFiles(context)
        }.start()

        Log.i(TAG, "Duress: Reset complete — app is fresh, background cleanup started")
    }

    /**
     * Rename all files in a directory tree with _tobedeleted_ prefix.
     * These files are already unreadable (old key destroyed) — this is just cleanup.
     */
    private fun markForDeletion(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                markForDeletion(file)
            } else if (!file.name.startsWith(DELETE_PREFIX)) {
                val marked = File(file.parentFile, "$DELETE_PREFIX${file.name}")
                file.renameTo(marked)
            }
        }
    }

    /**
     * Delete all files with _tobedeleted_ prefix. Called from background thread.
     * Also called on app startup to clean up any leftovers from interrupted wipes.
     */
    fun deleteMarkedFiles(context: Context) {
        val vaultDir = File(context.filesDir, "vault")
        if (!vaultDir.exists()) return

        var deleted = 0
        vaultDir.walkTopDown().filter { it.isFile && it.name.startsWith(DELETE_PREFIX) }.forEach { file ->
            if (file.delete()) deleted++
        }

        // Clean up empty directories
        vaultDir.walkBottomUp().filter { it.isDirectory && it != vaultDir }.forEach { dir ->
            if (dir.listFiles()?.isEmpty() == true) dir.delete()
        }

        if (deleted > 0) {
            Log.i(TAG, "Duress cleanup: deleted $deleted orphaned files")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun restorePrefs(prefs: android.content.SharedPreferences, saved: Map<String, Any?>) {
        val editor = prefs.edit()
        editor.clear()
        for ((key, value) in saved) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.apply()
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
