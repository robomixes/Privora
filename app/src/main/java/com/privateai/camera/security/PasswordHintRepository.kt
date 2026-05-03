// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Encrypted storage for password hints.
 * Each hint is serialized to JSON, then AES-256-GCM encrypted.
 * Stored as individual .hint.enc files in vault/passwords/.
 */

data class PasswordHint(
    val id: String = UUID.randomUUID().toString(),
    val serviceName: String,
    val category: HintCategory = HintCategory.OTHER,
    val usernameHint: String = "",
    val passwordHint: String = "",
    val pinHint: String = "",
    val url: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

enum class HintCategory(val label: String, val icon: String) {
    EMAIL("Email", "✉\uFE0F"),
    SOCIAL("Social", "\uD83D\uDCAC"),
    BANKING("Banking", "\uD83C\uDFE6"),
    SHOPPING("Shopping", "\uD83D\uDED2"),
    WORK("Work", "\uD83D\uDCBC"),
    WIFI("Wi-Fi", "\uD83D\uDCF6"),
    GAMING("Gaming", "\uD83C\uDFAE"),
    OTHER("Other", "\uD83D\uDD11")
}

class PasswordHintRepository(private val baseDir: File, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "PasswordHintRepo"
    }

    private val hintsDir = File(baseDir, "passwords").also { it.mkdirs() }

    fun save(hint: PasswordHint) {
        val json = JSONObject().apply {
            put("id", hint.id)
            put("serviceName", hint.serviceName)
            put("category", hint.category.name)
            put("usernameHint", hint.usernameHint)
            put("passwordHint", hint.passwordHint)
            put("pinHint", hint.pinHint)
            put("url", hint.url)
            put("notes", hint.notes)
            put("isFavorite", hint.isFavorite)
            put("createdAt", hint.createdAt)
            put("modifiedAt", hint.modifiedAt)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(hintsDir, "${hint.id}.hint.enc"))
    }

    fun listAll(): List<PasswordHint> {
        return (hintsDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".hint.enc") && !it.name.startsWith("_tobedeleted_") }
            .mapNotNull { file ->
                try {
                    val json = String(crypto.decryptFile(file), Charsets.UTF_8)
                    val obj = JSONObject(json)
                    PasswordHint(
                        id = obj.getString("id"),
                        serviceName = obj.getString("serviceName"),
                        category = try { HintCategory.valueOf(obj.optString("category", "OTHER")) } catch (_: Exception) { HintCategory.OTHER },
                        usernameHint = obj.optString("usernameHint", ""),
                        passwordHint = obj.optString("passwordHint", ""),
                        pinHint = obj.optString("pinHint", ""),
                        url = obj.optString("url", ""),
                        notes = obj.optString("notes", ""),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        createdAt = obj.optLong("createdAt", 0L),
                        modifiedAt = obj.optLong("modifiedAt", 0L)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load hint: ${e.message}")
                    null
                }
            }
            .sortedByDescending { it.modifiedAt }
    }

    fun delete(id: String) {
        File(hintsDir, "$id.hint.enc").delete()
    }

    fun search(query: String): List<PasswordHint> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return listAll()
        return listAll().filter { hint ->
            hint.serviceName.lowercase().contains(q) ||
            hint.usernameHint.lowercase().contains(q) ||
            hint.passwordHint.lowercase().contains(q) ||
            hint.notes.lowercase().contains(q) ||
            hint.url.lowercase().contains(q)
        }
    }

    fun listByCategory(category: HintCategory): List<PasswordHint> =
        listAll().filter { it.category == category }

    fun count(): Int = (hintsDir.listFiles() ?: emptyArray()).count { it.name.endsWith(".hint.enc") }
}
