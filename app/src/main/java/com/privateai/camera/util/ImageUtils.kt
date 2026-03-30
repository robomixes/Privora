package com.privateai.camera.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
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
    val uri = saveBitmapToCache(context, bitmap)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Try Google Lens first
    val lensIntent = Intent(intent).apply { setPackage("com.google.ar.lens") }
    if (lensIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(lensIntent)
        return
    }

    // Try Google app
    val googleIntent = Intent(intent).apply { setPackage("com.google.android.googlequicksearchbox") }
    if (googleIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(googleIntent)
        return
    }

    // Fallback: share chooser
    context.startActivity(Intent.createChooser(intent, "Search with image"))
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): android.net.Uri {
    val file = File(context.cacheDir, "shared_detection.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
