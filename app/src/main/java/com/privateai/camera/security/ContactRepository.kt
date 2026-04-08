package com.privateai.camera.security

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class PrivateContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val photoId: String? = null,
    val group: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

class ContactRepository(
    private val baseDir: File,
    private val crypto: CryptoManager,
    private val database: PrivoraDatabase
) {

    companion object {
        private const val TAG = "ContactRepository"
        private const val TABLE = "contacts"
    }

    init { baseDir.mkdirs() }

    private val db get() = database.db

    fun saveContact(contact: PrivateContact) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", contact.id)
            put("name", contact.name)
            put("phone", contact.phone)
            put("email", contact.email)
            put("notes", contact.notes)
            put("photo_id", contact.photoId)
            put("contact_group", contact.group)
            put("is_favorite", if (contact.isFavorite) 1 else 0)
            put("created_at", contact.createdAt)
            put("modified_at", now)
        }
        try {
            db.insertWithOnConflict(TABLE, null, values, net.zetetic.database.sqlcipher.SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save contact: ${e.message}")
        }
    }

    fun deleteContact(id: String) {
        try {
            db.delete(TABLE, "id = ?", arrayOf(id))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete contact: ${e.message}")
        }
    }

    fun listContacts(): List<PrivateContact> {
        return try {
            val cursor = db.rawQuery("SELECT * FROM $TABLE ORDER BY name ASC", null)
            val contacts = mutableListOf<PrivateContact>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    contacts.add(cursorToContact(c))
                }
            }
            contacts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts: ${e.message}")
            emptyList()
        }
    }

    fun contactCount(): Int {
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
            cursor.use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count contacts: ${e.message}")
            0
        }
    }

    fun getGroups(): List<String> {
        return try {
            val cursor = db.rawQuery(
                "SELECT DISTINCT contact_group FROM $TABLE WHERE contact_group != '' ORDER BY contact_group",
                null
            )
            val groups = mutableListOf<String>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    groups.add(c.getString(0))
                }
            }
            groups
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load groups: ${e.message}")
            emptyList()
        }
    }

    // --- Profile photo methods (file-based encryption, unchanged) ---

    private val photosDir get() = File(baseDir, "photos").also { it.mkdirs() }

    /**
     * Save a face-cropped profile photo. Returns the photoId.
     * The bitmap is stored as an independent encrypted copy -- survives gallery deletion.
     */
    fun saveProfilePhoto(contactId: String, faceBitmap: Bitmap): String {
        val photoId = "profile_$contactId"
        val jpegBytes = ByteArrayOutputStream().use { out ->
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        crypto.encryptToFile(jpegBytes, File(photosDir, "$photoId.enc"))
        return photoId
    }

    /**
     * Load a profile photo bitmap. Returns null if not found.
     */
    fun loadProfilePhoto(contactId: String): Bitmap? {
        val file = File(photosDir, "profile_$contactId.enc")
        if (!file.exists()) return null
        return try {
            val bytes = crypto.decryptFile(file)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load profile photo: ${e.message}")
            null
        }
    }

    /**
     * Delete a profile photo.
     */
    fun deleteProfilePhoto(contactId: String) {
        File(photosDir, "profile_$contactId.enc").delete()
    }

    // --- Internal helpers ---

    private fun cursorToContact(c: android.database.Cursor): PrivateContact {
        return PrivateContact(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            phone = c.getString(c.getColumnIndexOrThrow("phone")) ?: "",
            email = c.getString(c.getColumnIndexOrThrow("email")) ?: "",
            notes = c.getString(c.getColumnIndexOrThrow("notes")) ?: "",
            photoId = c.getString(c.getColumnIndexOrThrow("photo_id")),
            group = c.getString(c.getColumnIndexOrThrow("contact_group")) ?: "",
            isFavorite = c.getInt(c.getColumnIndexOrThrow("is_favorite")) != 0,
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            modifiedAt = c.getLong(c.getColumnIndexOrThrow("modified_at"))
        )
    }
}
