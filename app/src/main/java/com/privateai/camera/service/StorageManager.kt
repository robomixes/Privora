package com.privateai.camera.service

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class StorageInfo(
    val vaultSizeBytes: Long,
    val notesSizeBytes: Long,
    val cacheSizeBytes: Long,
    val totalUsedBytes: Long,
    val deviceFreeBytes: Long,
    val deviceTotalBytes: Long,
    val usagePercent: Float
)

object StorageManager {

    fun getStorageInfo(context: Context): StorageInfo {
        val vaultDir = File(context.filesDir, "vault")
        val notesDir = File(context.filesDir, "vault/notes")
        val cacheDir = context.cacheDir

        val vaultSize = dirSize(vaultDir) - dirSize(notesDir) // Exclude notes from vault count
        val notesSize = dirSize(notesDir)
        val cacheSize = dirSize(cacheDir)
        val totalUsed = vaultSize + notesSize + cacheSize

        val stat = StatFs(Environment.getDataDirectory().path)
        val deviceFree = stat.availableBytes
        val deviceTotal = stat.totalBytes

        val usagePercent = if (deviceTotal > 0) {
            ((deviceTotal - deviceFree).toFloat() / deviceTotal * 100)
        } else 0f

        return StorageInfo(
            vaultSizeBytes = vaultSize,
            notesSizeBytes = notesSize,
            cacheSizeBytes = cacheSize,
            totalUsedBytes = totalUsed,
            deviceFreeBytes = deviceFree,
            deviceTotalBytes = deviceTotal,
            usagePercent = usagePercent
        )
    }

    fun isStorageLow(context: Context): Boolean {
        val info = getStorageInfo(context)
        return info.deviceFreeBytes < 500 * 1024 * 1024 // Less than 500MB free
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    fun clearCache(context: Context): Long {
        val cacheDir = context.cacheDir
        val sizeBefore = dirSize(cacheDir)
        cacheDir.listFiles()?.forEach { it.delete() }
        return sizeBefore
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
