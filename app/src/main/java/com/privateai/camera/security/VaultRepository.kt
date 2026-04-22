package com.privateai.camera.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class VaultMediaType { PHOTO, VIDEO, PDF, FILE }

enum class VaultCategory(val label: String, val dirName: String) {
    CAMERA("Camera", "camera"),
    VIDEO("Videos", "video"),
    SCAN("Scans", "scan"),
    DETECT("Detections", "detect"),
    REPORTS("Reports", "reports"),
    FILES("Files", "files")
}

data class VaultPhoto(
    val id: String,
    val timestamp: Long,
    val category: VaultCategory,
    val encryptedFile: File,
    val thumbnailFile: File,
    val mediaType: VaultMediaType = VaultMediaType.PHOTO
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
     * Save a video file to the vault (encrypted).
     * CRITICAL ORDER: extract thumbnail BEFORE encrypting (retriever needs raw file).
     */
    fun saveVideo(tempVideoFile: File, category: VaultCategory = VaultCategory.VIDEO): VaultPhoto {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val dir = categoryDir(category)

        // 1. Extract first frame as thumbnail (must happen before encryption/deletion)
        val retriever = MediaMetadataRetriever()
        var thumbBytes: ByteArray
        try {
            retriever.setDataSource(tempVideoFile.absolutePath)
            val frame = retriever.getFrameAtTime(0) ?: retriever.getFrameAtTime(1000000) // fallback to 1s
            retriever.release()

            if (frame != null) {
                val scale = THUMB_SIZE.toFloat() / maxOf(frame.width, frame.height)
                val thumb = Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt(),
                    (frame.height * scale).toInt(),
                    true
                )
                thumbBytes = ByteArrayOutputStream().use { out ->
                    thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    out.toByteArray()
                }
                thumb.recycle()
                frame.recycle()
            } else {
                // No frame available — use empty placeholder
                thumbBytes = ByteArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video thumbnail: ${e.message}")
            retriever.release()
            thumbBytes = ByteArray(0)
        }

        // 2. Encrypt video file
        val videoEncFile = File(dir, "$id.vid.enc")
        crypto.encryptFile(tempVideoFile, videoEncFile)

        // 3. Encrypt thumbnail
        val thumbFile = File(dir, "$id.vid.thumb.enc")
        if (thumbBytes.isNotEmpty()) {
            crypto.encryptToFile(thumbBytes, thumbFile)
        }

        // 4. Delete temp file
        tempVideoFile.delete()

        val videoSize = videoEncFile.length() / 1024
        Log.d(TAG, "Video saved: $id ($category, ${videoSize}KB)")

        return VaultPhoto(id, timestamp, category, videoEncFile, thumbFile, VaultMediaType.VIDEO)
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
     * Decrypt a video to a temp file for playback. Caller must delete the temp file when done.
     */
    fun decryptVideoToTempFile(item: VaultPhoto): File? {
        return try {
            val decrypted = crypto.decryptFile(item.encryptedFile)
            val tempFile = File(context.cacheDir, "playback_${item.id}.mp4")
            tempFile.writeBytes(decrypted)
            Log.d(TAG, "Video decrypted to temp: ${tempFile.absolutePath} (${decrypted.size / 1024}KB)")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt video: ${e.message}")
            null
        }
    }

    /**
     * Replace a photo's content with a new bitmap. Overwrites encrypted file + thumbnail.
     */
    fun replacePhoto(photo: VaultPhoto, newBitmap: Bitmap) {
        val jpegBytes = ByteArrayOutputStream().use { out ->
            newBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }
        val scale = THUMB_SIZE.toFloat() / maxOf(newBitmap.width, newBitmap.height)
        val thumb = Bitmap.createScaledBitmap(
            newBitmap, (newBitmap.width * scale).toInt(), (newBitmap.height * scale).toInt(), true
        )
        val thumbBytes = ByteArrayOutputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }
        thumb.recycle()
        crypto.encryptToFile(jpegBytes, photo.encryptedFile)
        crypto.encryptToFile(thumbBytes, photo.thumbnailFile)
        Log.d(TAG, "Photo replaced: ${photo.id} (${jpegBytes.size / 1024}KB)")
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
     * List photos and videos in a specific category, sorted by newest first.
     */
    fun listPhotos(category: VaultCategory): List<VaultPhoto> {
        val dir = categoryDir(category)
        Log.d(TAG, "listPhotos($category): dir=${dir.absolutePath}, exists=${dir.exists()}")
        val files = dir.listFiles()
        Log.d(TAG, "listPhotos($category): files found=${files?.size ?: 0}")
        if (files == null) return emptyList()

        val items = mutableListOf<VaultPhoto>()

        // Photos: {id}.enc (excluding .thumb.enc, .vid.enc, .pdf.enc, .file.enc, _tobedeleted_)
        files.filter {
            it.name.endsWith(".enc") &&
                !it.name.endsWith(".thumb.enc") &&
                !it.name.endsWith(".vid.enc") &&
                !it.name.endsWith(".pdf.enc") &&
                !it.name.endsWith(".file.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".enc")
            items.add(VaultPhoto(id, file.lastModified(), category, file, File(dir, "$id.thumb.enc"), VaultMediaType.PHOTO))
        }

        // Generic files: {name}.file.enc (docs, spreadsheets, text, etc.)
        files.filter {
            it.name.endsWith(".file.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".file.enc")
            items.add(VaultPhoto(id, file.lastModified(), category, file, file, VaultMediaType.FILE))
        }

        // Videos: {id}.vid.enc (excluding .vid.thumb.enc, _tobedeleted_)
        files.filter {
            it.name.endsWith(".vid.enc") &&
                !it.name.endsWith(".vid.thumb.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".vid.enc")
            items.add(VaultPhoto(id, file.lastModified(), category, file, File(dir, "$id.vid.thumb.enc"), VaultMediaType.VIDEO))
        }

        // PDFs: {name}.pdf.enc (_tobedeleted_ excluded)
        files.filter {
            it.name.endsWith(".pdf.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".pdf.enc")
            items.add(VaultPhoto(id, file.lastModified(), category, file, file, VaultMediaType.PDF))
        }

        Log.d(TAG, "listPhotos($category): items=${items.size} (photos=${items.count { it.mediaType == VaultMediaType.PHOTO }}, videos=${items.count { it.mediaType == VaultMediaType.VIDEO }}, pdfs=${items.count { it.mediaType == VaultMediaType.PDF }})")

        return items.sortedByDescending { it.timestamp }
    }

    /**
     * List all photos across all categories, sorted by newest first.
     */
    fun listAllPhotos(): List<VaultPhoto> {
        return VaultCategory.entries.flatMap { listPhotos(it) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * List ALL photos (not videos, not PDFs) from all categories + custom folders.
     * Used by the virtual "Photos" smart view.
     */
    fun listAllPhotosOnly(folderItems: List<VaultPhoto> = emptyList()): List<VaultPhoto> {
        val fromCategories = VaultCategory.entries.flatMap { listPhotos(it) }
            .filter { it.mediaType == VaultMediaType.PHOTO }
        return (fromCategories + folderItems.filter { it.mediaType == VaultMediaType.PHOTO })
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }

    /**
     * List ALL videos from all categories + custom folders.
     * Used by the virtual "Videos" smart view.
     */
    fun listAllVideosOnly(folderItems: List<VaultPhoto> = emptyList()): List<VaultPhoto> {
        val fromCategories = VaultCategory.entries.flatMap { listPhotos(it) }
            .filter { it.mediaType == VaultMediaType.VIDEO }
        return (fromCategories + folderItems.filter { it.mediaType == VaultMediaType.VIDEO })
            .distinctBy { it.id }
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
        // Use .pdf.enc for PDFs, .file.enc for everything else (distinct from .enc photos)
        val ext = if (filename.lowercase().endsWith(".pdf")) ".pdf.enc" else ".file.enc"
        val encFile = File(dir, "$filename$ext")
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
            .filter { it.name.endsWith(".enc") && !it.name.endsWith(".thumb.enc") && !it.name.startsWith("_tobedeleted_") }
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Save a photo to a custom folder.
     */
    fun savePhotoToFolder(bitmap: Bitmap, folderDir: File): VaultPhoto {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        folderDir.mkdirs()

        val jpegBytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }
        val scale = THUMB_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val thumb = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        val thumbBytes = ByteArrayOutputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }
        thumb.recycle()

        val photoFile = File(folderDir, "$id.enc")
        val thumbFile = File(folderDir, "$id.thumb.enc")
        crypto.encryptToFile(jpegBytes, photoFile)
        crypto.encryptToFile(thumbBytes, thumbFile)

        Log.d(TAG, "Photo saved to folder: $id (${jpegBytes.size / 1024}KB)")
        return VaultPhoto(id, timestamp, VaultCategory.FILES, photoFile, thumbFile)
    }

    /**
     * List items in a custom folder directory.
     */
    fun listFolderItems(folderDir: File): List<VaultPhoto> {
        if (!folderDir.exists()) return emptyList()
        val files = folderDir.listFiles() ?: return emptyList()
        val items = mutableListOf<VaultPhoto>()

        files.filter {
            it.isFile && it.name.endsWith(".enc") &&
                !it.name.endsWith(".thumb.enc") &&
                !it.name.endsWith(".vid.enc") &&
                !it.name.endsWith(".pdf.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".enc")
            items.add(VaultPhoto(id, file.lastModified(), VaultCategory.FILES, file, File(folderDir, "$id.thumb.enc")))
        }

        files.filter {
            it.name.endsWith(".vid.enc") && !it.name.endsWith(".vid.thumb.enc") && !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".vid.enc")
            items.add(VaultPhoto(id, file.lastModified(), VaultCategory.FILES, file, File(folderDir, "$id.vid.thumb.enc"), VaultMediaType.VIDEO))
        }

        files.filter {
            it.name.endsWith(".pdf.enc") && !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".pdf.enc")
            items.add(VaultPhoto(id, file.lastModified(), VaultCategory.FILES, file, file, VaultMediaType.PDF))
        }

        return items.sortedByDescending { it.timestamp }
    }

    /**
     * Move a photo to a custom folder directory.
     */
    fun moveToFolder(photo: VaultPhoto, targetDir: File) {
        targetDir.mkdirs()
        val newEncFile = File(targetDir, photo.encryptedFile.name)
        val newThumbFile = File(targetDir, photo.thumbnailFile.name)
        photo.encryptedFile.renameTo(newEncFile)
        if (photo.thumbnailFile.exists() && photo.thumbnailFile != photo.encryptedFile) {
            photo.thumbnailFile.renameTo(newThumbFile)
        }
        Log.d(TAG, "Photo moved to folder: ${photo.id}")
    }

    /**
     * Get vault size in bytes.
     */
    fun getVaultSize(): Long {
        return vaultDir.walkTopDown().filter { it.isFile }.fold(0L) { acc, file -> acc + file.length() }
    }

    // ===== Trash / Recycle Bin =====

    private val trashDir = File(vaultDir, "_trash").also { it.mkdirs() }
    private val trashIndexFile = File(trashDir, "_index.enc")

    data class TrashedItem(
        val id: String,
        val originalCategory: VaultCategory,
        val trashedAt: Long,
        val encryptedFile: File,
        val thumbnailFile: File,
        val mediaType: VaultMediaType
    )

    /** Move a photo to trash instead of permanently deleting. */
    fun moveToTrash(photo: VaultPhoto) {
        // Move files to trash dir
        val trashedEnc = File(trashDir, photo.encryptedFile.name)
        val trashedThumb = File(trashDir, photo.thumbnailFile.name)
        photo.encryptedFile.renameTo(trashedEnc)
        if (photo.thumbnailFile.exists() && photo.thumbnailFile != photo.encryptedFile) {
            photo.thumbnailFile.renameTo(trashedThumb)
        }
        // Add to trash index
        val items = loadTrashIndex().toMutableList()
        items.add(TrashedItem(photo.id, photo.category, System.currentTimeMillis(), trashedEnc, trashedThumb, photo.mediaType))
        saveTrashIndex(items)
        Log.d(TAG, "Moved to trash: ${photo.id}")
    }

    /** Restore a photo from trash to its original category. */
    fun restoreFromTrash(itemId: String) {
        val items = loadTrashIndex().toMutableList()
        val item = items.find { it.id == itemId } ?: return
        val targetDir = categoryDir(item.originalCategory)
        // Move files back
        val restoredEnc = File(targetDir, item.encryptedFile.name)
        val restoredThumb = File(targetDir, item.thumbnailFile.name)
        item.encryptedFile.renameTo(restoredEnc)
        if (item.thumbnailFile.exists() && item.thumbnailFile != item.encryptedFile) {
            item.thumbnailFile.renameTo(restoredThumb)
        }
        items.removeAll { it.id == itemId }
        saveTrashIndex(items)
        Log.d(TAG, "Restored from trash: $itemId")
    }

    /** List all items in trash. */
    fun listTrash(): List<TrashedItem> = loadTrashIndex().sortedByDescending { it.trashedAt }

    /** Permanently delete a single item from trash. */
    fun permanentDeleteFromTrash(itemId: String) {
        val items = loadTrashIndex().toMutableList()
        val item = items.find { it.id == itemId } ?: return
        item.encryptedFile.delete()
        item.thumbnailFile.delete()
        items.removeAll { it.id == itemId }
        saveTrashIndex(items)
        Log.d(TAG, "Permanently deleted from trash: $itemId")
    }

    /** Empty all trash. */
    fun emptyTrash() {
        val items = loadTrashIndex()
        items.forEach { it.encryptedFile.delete(); it.thumbnailFile.delete() }
        saveTrashIndex(emptyList())
        Log.d(TAG, "Trash emptied: ${items.size} items")
    }

    /** Auto-purge items older than maxDays. Call on vault open. */
    fun autoPurgeTrash(maxDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - maxDays.toLong() * 24 * 60 * 60 * 1000
        val items = loadTrashIndex().toMutableList()
        val expired = items.filter { it.trashedAt < cutoff }
        if (expired.isEmpty()) return
        expired.forEach { it.encryptedFile.delete(); it.thumbnailFile.delete() }
        items.removeAll { it.trashedAt < cutoff }
        saveTrashIndex(items)
        Log.d(TAG, "Auto-purged ${expired.size} items from trash")
    }

    /** Trash item count. */
    fun trashCount(): Int = loadTrashIndex().size

    private fun loadTrashIndex(): List<TrashedItem> {
        if (!trashIndexFile.exists()) return emptyList()
        return try {
            val json = String(crypto.decryptFile(trashIndexFile), Charsets.UTF_8)
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                TrashedItem(
                    id = obj.getString("id"),
                    originalCategory = try { VaultCategory.valueOf(obj.getString("category")) } catch (_: Exception) { VaultCategory.CAMERA },
                    trashedAt = obj.getLong("trashedAt"),
                    encryptedFile = File(trashDir, obj.getString("encFile")),
                    thumbnailFile = File(trashDir, obj.getString("thumbFile")),
                    mediaType = try { VaultMediaType.valueOf(obj.getString("mediaType")) } catch (_: Exception) { VaultMediaType.PHOTO }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trash index: ${e.message}")
            emptyList()
        }
    }

    private fun saveTrashIndex(items: List<TrashedItem>) {
        try {
            val arr = org.json.JSONArray()
            items.forEach { item ->
                arr.put(org.json.JSONObject().apply {
                    put("id", item.id)
                    put("category", item.originalCategory.name)
                    put("trashedAt", item.trashedAt)
                    put("encFile", item.encryptedFile.name)
                    put("thumbFile", item.thumbnailFile.name)
                    put("mediaType", item.mediaType.name)
                })
            }
            crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), trashIndexFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save trash index: ${e.message}")
        }
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
