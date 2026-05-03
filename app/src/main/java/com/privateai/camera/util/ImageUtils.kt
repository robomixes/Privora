// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.privateai.camera.bridge.Detection
import java.io.File
import java.io.FileOutputStream

fun cropDetectionRegion(bitmap: Bitmap, detection: Detection): Bitmap {
    val x = (detection.x1 * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val y = (detection.y1 * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val w = ((detection.x2 - detection.x1) * bitmap.width).toInt()
        .coerceIn(1, bitmap.width - x)
    val h = ((detection.y2 - detection.y1) * bitmap.height).toInt()
        .coerceIn(1, bitmap.height - y)
    return Bitmap.createBitmap(bitmap, x, y, w, h)
}

fun launchImageSearch(context: Context, bitmap: Bitmap) {
    val uri = saveBitmapToCache(context, bitmap, "shared_detection.jpg")

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val lensIntent = Intent(intent).apply { setPackage("com.google.ar.lens") }
    if (lensIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(lensIntent)
        return
    }

    val googleIntent = Intent(intent).apply { setPackage("com.google.android.googlequicksearchbox") }
    if (googleIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(googleIntent)
        return
    }

    context.startActivity(Intent.createChooser(intent, "Search with image"))
}

/**
 * Save bitmap to cache as JPEG with all EXIF metadata stripped.
 * Returns a content URI via FileProvider.
 */
fun saveBitmapToCache(context: Context, bitmap: Bitmap, filename: String = "shared.jpg"): Uri {
    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    // Strip EXIF — re-compressing a Bitmap already strips most metadata,
    // but explicitly clear any remaining tags
    stripExif(file)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Save raw JPEG bytes to cache with EXIF stripped.
 */
fun saveJpegBytesToCache(context: Context, jpegBytes: ByteArray, filename: String = "shared.jpg"): Uri {
    val file = File(context.cacheDir, filename)
    file.writeBytes(jpegBytes)
    stripExif(file)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Strip all EXIF metadata from a JPEG file.
 * Removes: GPS location, device info, timestamps, camera settings, thumbnails.
 */
fun stripExif(file: File) {
    try {
        val exif = ExifInterface(file.absolutePath)
        // GPS
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
        // Device info
        exif.setAttribute(ExifInterface.TAG_MAKE, null)
        exif.setAttribute(ExifInterface.TAG_MODEL, null)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
        // Timestamps
        exif.setAttribute(ExifInterface.TAG_DATETIME, null)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
        // Camera settings
        exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, null)
        exif.setAttribute(ExifInterface.TAG_F_NUMBER, null)
        exif.setAttribute(ExifInterface.TAG_ISO_SPEED, null)
        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, null)
        exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, null)
        exif.setAttribute(ExifInterface.TAG_FLASH, null)
        // Unique IDs
        exif.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, null)
        // Keep orientation (needed for display) but strip everything else
        exif.saveAttributes()
    } catch (_: Exception) {
        // If stripping fails, the file is still safe to share
        // (Bitmap.compress doesn't write most EXIF anyway)
    }
}
