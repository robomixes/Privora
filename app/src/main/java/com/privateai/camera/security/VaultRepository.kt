package com.privateai.camera.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class VaultCategory(val label: String, val dirName: String) {
    CAMERA("Camera", "camera"),
    SCAN("Scans", "scan"),
    FILES("Files", "files")
}

data class VaultPhoto(
    val id: String,
    val timestamp: Long,
    val category: VaultCategory,
    val encryptedFile: File,
    val thumbnailFile: File
)

/**
 * Manages encrypted photo storage.
 * Photos and thumbnails are AES-256-GCM encrypted.
 * Never writes plaintext to disk.
 */
class VaultRepository(private val context: Context, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "VaultRepository"
        private const val VAULT_DIR = "vault"
        private const val THUMB_SIZE = 200
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIR).also { it.mkdirs() }
    }

    private fun categoryDir(category: VaultCategory): File {
        return File(vaultDir, category.dirName).also { it.mkdirs() }
    }

    /**
     * Save a bitmap to the vault (encrypted).
     */
    fun savePhoto(bitmap: Bitmap, category: VaultCategory = VaultCategory.CAMERA): VaultPhoto {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val dir = categoryDir(category)

        val jpegBytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }

        val scale = THUMB_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val thumb = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
        val thumbBytes = ByteArrayOutputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }
        thumb.recycle()

        val photoFile = File(dir, "$id.enc")
        val thumbFile = File(dir, "$id.thumb.enc")

        crypto.encryptToFile(jpegBytes, photoFile)
        crypto.encryptToFile(thumbBytes, thumbFile)

        Log.d(TAG, "Photo saved: $id ($category, ${jpegBytes.size / 1024}KB)")

        return VaultPhoto(id, timestamp, category, photoFile, thumbFile)
    }

    /**
     * Decrypt and return a thumbnail bitmap. Never touches disk.
     */
    fun loadThumbnail(photo: VaultPhoto): Bitmap? {
        return try {
            Log.d(TAG, "loadThumbnail: ${photo.id}, file=${photo.thumbnailFile.absolutePath}, exists=${photo.thumbnailFile.exists()}, size=${photo.thumbnailFile.length()}")
            val decrypted = crypto.decryptFile(photo.thumbnailFile)
            Log.d(TAG, "loadThumbnail: decrypted ${decrypted.size} bytes")
            val bmp = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
            Log.d(TAG, "loadThumbnail: bitmap=${bmp?.width}x${bmp?.height}")
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt thumbnail: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Decrypt and return the full-resolution photo. Never touches disk.
     */
    fun loadFullPhoto(photo: VaultPhoto): Bitmap? {
        return try {
            val decrypted = crypto.decryptFile(photo.encryptedFile)
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt photo: ${e.message}")
            null
        }
    }

    /**
     * Decrypt and return photo as JPEG bytes (for sharing).
     */
    fun loadPhotoBytes(photo: VaultPhoto): ByteArray? {
        return try {
            crypto.decryptFile(photo.encryptedFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt photo bytes: ${e.message}")
            null
        }
    }

    /**
     * Delete a photo from the vault.
     */
    fun deletePhoto(photo: VaultPhoto) {
        photo.encryptedFile.delete()
        photo.thumbnailFile.delete()
        Log.d(TAG, "Photo deleted: ${photo.id}")
    }

    /**
     * List photos in a specific category, sorted by newest first.
     */
    fun listPhotos(category: VaultCategory): List<VaultPhoto> {
        val dir = categoryDir(category)
        Log.d(TAG, "listPhotos($category): dir=${dir.absolutePath}, exists=${dir.exists()}")
        val files = dir.listFiles()
        Log.d(TAG, "listPhotos($category): files found=${files?.size ?: 0}")
        if (files == null) return emptyList()

        val photoIds = files
            .filter { it.name.endsWith(".enc") && !it.name.endsWith(".thumb.enc") }
            .map { it.name.removeSuffix(".enc") }

        Log.d(TAG, "listPhotos($category): photoIds=${photoIds.size}")

        return photoIds.map { id ->
            VaultPhoto(
                id = id,
                timestamp = File(dir, "$id.enc").lastModified(),
                category = category,
                encryptedFile = File(dir, "$id.enc"),
                thumbnailFile = File(dir, "$id.thumb.enc")
            )
        }.sortedByDescending { it.timestamp }
    }

    /**
     * List all photos across all categories, sorted by newest first.
     */
    fun listAllPhotos(): List<VaultPhoto> {
        return VaultCategory.entries.flatMap { listPhotos(it) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Get count per category.
     */
    fun countByCategory(): Map<VaultCategory, Int> {
        return VaultCategory.entries.associateWith { listPhotos(it).size }
    }

    /**
     * Save a raw file (e.g. PDF) encrypted to the vault.
     * Returns the encrypted file path.
     */
    fun saveFile(data: ByteArray, filename: String, category: VaultCategory = VaultCategory.FILES): File {
        val dir = categoryDir(category)
        val encFile = File(dir, "$filename.enc")
        crypto.encryptToFile(data, encFile)
        Log.d(TAG, "File saved: $filename ($category, ${data.size / 1024}KB)")
        return encFile
    }

    /**
     * Decrypt a raw file and return bytes.
     */
    fun loadFile(encFile: File): ByteArray? {
        return try {
            crypto.decryptFile(encFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt file: ${e.message}")
            null
        }
    }

    /**
     * List encrypted files (non-photo) in a category.
     */
    fun listFiles(category: VaultCategory = VaultCategory.FILES): List<File> {
        val dir = categoryDir(category)
        return (dir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".enc") && !it.name.endsWith(".thumb.enc") }
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Get vault size in bytes.
     */
    fun getVaultSize(): Long {
        return vaultDir.listFiles()?.sumOf { it.length() } ?: 0
    }

    /**
     * Wipe all vault files. Used by duress PIN.
     */
    fun wipeAll() {
        vaultDir.listFiles()?.forEach {
            if (it.isDirectory) it.deleteRecursively() else it.delete()
        }
        Log.i(TAG, "Vault wiped")
    }
}
