// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SecureNote(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String>,
    val color: Int = 0,       // 0=default, 1-8 = color presets
    val pinned: Boolean = false,
    val attachments: List<String> = emptyList(), // vault photo IDs
    val audioAttachments: List<String> = emptyList(), // encrypted audio file IDs
    val personId: String? = null,
    val createdAt: Long,
    val modifiedAt: Long
)

/**
 * Manages encrypted notes storage.
 * Each note is serialized to JSON, then AES-256-GCM encrypted.
 * Notes are stored as individual .note.enc files in the vault/notes/ directory.
 */
class NoteRepository(private val notesDir: File, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "NoteRepository"
    }

    init {
        notesDir.mkdirs()
    }

    fun saveNote(note: SecureNote): SecureNote {
        val updated = note.copy(modifiedAt = System.currentTimeMillis())
        val json = noteToJson(updated)
        val file = File(notesDir, "${updated.id}.note.enc")
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), file)
        Log.d(TAG, "Note saved: ${updated.id} (${updated.title})")
        return updated
    }

    fun createNote(title: String, content: String, tags: List<String> = emptyList(), attachments: List<String> = emptyList(), audioAttachments: List<String> = emptyList(), personId: String? = null): SecureNote {
        val now = System.currentTimeMillis()
        val note = SecureNote(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            tags = tags,
            attachments = attachments,
            audioAttachments = audioAttachments,
            personId = personId,
            createdAt = now,
            modifiedAt = now
        )
        return saveNote(note)
    }

    fun loadNote(id: String): SecureNote? {
        return try {
            val file = File(notesDir, "$id.note.enc")
            if (!file.exists()) return null
            val decrypted = crypto.decryptFile(file)
            jsonToNote(String(decrypted, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load note $id: ${e.message}")
            null
        }
    }

    fun listNotes(): List<SecureNote> {
        val files = notesDir.listFiles() ?: return emptyList()
        return files
            .filter { it.name.endsWith(".note.enc") && !it.name.startsWith("_tobedeleted_") }
            .mapNotNull { file ->
                val id = file.name.removeSuffix(".note.enc")
                loadNote(id)
            }
            .sortedWith(compareByDescending<SecureNote> { it.pinned }.thenByDescending { it.modifiedAt })
    }

    fun deleteNote(id: String) {
        File(notesDir, "$id.note.enc").delete()
        File(notesDir, "$id.draft.enc").delete()
        Log.d(TAG, "Note deleted: $id")
    }

    /**
     * Save an in-progress editor state as an encrypted draft. Used to recover
     * unsaved edits if the editor gets unmounted by auto-lock before the user
     * taps Save. `id` is the note id for existing notes, or "__new__" for a
     * not-yet-created note.
     */
    fun saveDraft(
        id: String,
        title: String,
        content: String,
        tags: List<String>,
        attachments: List<String>,
        audioAttachments: List<String>,
        personId: String?
    ) {
        try {
            val json = JSONObject().apply {
                put("title", title)
                put("content", content)
                put("tags", JSONArray(tags))
                put("attachments", JSONArray(attachments))
                put("audioAttachments", JSONArray(audioAttachments))
                put("personId", personId ?: "")
                put("savedAt", System.currentTimeMillis())
            }.toString()
            crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(notesDir, "$id.draft.enc"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save draft for $id: ${e.message}")
        }
    }

    data class NoteDraft(
        val title: String,
        val content: String,
        val tags: List<String>,
        val attachments: List<String>,
        val audioAttachments: List<String>,
        val personId: String?,
        val savedAt: Long
    )

    fun loadDraft(id: String): NoteDraft? {
        return try {
            val file = File(notesDir, "$id.draft.enc")
            if (!file.exists()) return null
            val obj = JSONObject(String(crypto.decryptFile(file), Charsets.UTF_8))
            fun arrToList(key: String): List<String> = if (obj.has(key)) {
                val a = obj.getJSONArray(key); (0 until a.length()).map { a.getString(it) }
            } else emptyList()
            NoteDraft(
                title = obj.optString("title", ""),
                content = obj.optString("content", ""),
                tags = arrToList("tags"),
                attachments = arrToList("attachments"),
                audioAttachments = arrToList("audioAttachments"),
                personId = obj.optString("personId", "").ifEmpty { null },
                savedAt = obj.optLong("savedAt", 0L)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load draft for $id: ${e.message}")
            null
        }
    }

    fun clearDraft(id: String) {
        File(notesDir, "$id.draft.enc").delete()
    }

    fun searchNotes(query: String): List<SecureNote> {
        if (query.isBlank()) return listNotes()
        val lower = query.lowercase()
        return listNotes().filter {
            it.title.lowercase().contains(lower) ||
            it.content.lowercase().contains(lower) ||
            it.tags.any { tag -> tag.lowercase().contains(lower) }
        }
    }

    fun getAllTags(): List<String> {
        return listNotes()
            .flatMap { it.tags }
            .distinct()
            .sorted()
    }

    fun noteCount(): Int {
        return notesDir.listFiles()?.count { it.name.endsWith(".note.enc") && !it.name.startsWith("_tobedeleted_") } ?: 0
    }

    fun wipeAll() {
        notesDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "All notes wiped")
    }

    private fun noteToJson(note: SecureNote): String {
        return JSONObject().apply {
            put("id", note.id)
            put("title", note.title)
            put("content", note.content)
            put("tags", JSONArray(note.tags))
            put("color", note.color)
            put("pinned", note.pinned)
            put("attachments", JSONArray(note.attachments))
            put("audioAttachments", JSONArray(note.audioAttachments))
            put("personId", note.personId ?: "")
            put("createdAt", note.createdAt)
            put("modifiedAt", note.modifiedAt)
        }.toString()
    }

    private fun jsonToNote(json: String): SecureNote {
        val obj = JSONObject(json)
        val tagsArray = obj.getJSONArray("tags")
        val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }
        val attachments = if (obj.has("attachments")) {
            val arr = obj.getJSONArray("attachments")
            (0 until arr.length()).map { arr.getString(it) }
        } else emptyList()
        val audioAttachments = if (obj.has("audioAttachments")) {
            val arr = obj.getJSONArray("audioAttachments")
            (0 until arr.length()).map { arr.getString(it) }
        } else emptyList()
        return SecureNote(
            id = obj.getString("id"),
            title = obj.getString("title"),
            content = obj.getString("content"),
            tags = tags,
            color = obj.optInt("color", 0),
            pinned = obj.optBoolean("pinned", false),
            attachments = attachments,
            audioAttachments = audioAttachments,
            personId = obj.optString("personId", "").ifEmpty { null },
            createdAt = obj.getLong("createdAt"),
            modifiedAt = obj.getLong("modifiedAt")
        )
    }
}
