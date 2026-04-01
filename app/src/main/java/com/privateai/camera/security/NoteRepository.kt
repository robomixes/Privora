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

    fun createNote(title: String, content: String, tags: List<String> = emptyList()): SecureNote {
        val now = System.currentTimeMillis()
        val note = SecureNote(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            tags = tags,
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
        Log.d(TAG, "Note deleted: $id")
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
            put("createdAt", note.createdAt)
            put("modifiedAt", note.modifiedAt)
        }.toString()
    }

    private fun jsonToNote(json: String): SecureNote {
        val obj = JSONObject(json)
        val tagsArray = obj.getJSONArray("tags")
        val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }
        return SecureNote(
            id = obj.getString("id"),
            title = obj.getString("title"),
            content = obj.getString("content"),
            tags = tags,
            color = obj.optInt("color", 0),
            pinned = obj.optBoolean("pinned", false),
            createdAt = obj.getLong("createdAt"),
            modifiedAt = obj.getLong("modifiedAt")
        )
    }
}
