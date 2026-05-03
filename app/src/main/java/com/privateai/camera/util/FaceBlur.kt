// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Detects faces in a bitmap and returns a copy with faces blurred.
 * Uses ML Kit for face detection and pixel-level box blur for privacy.
 */
object FaceBlur {

    private const val TAG = "FaceBlur"

    /**
     * Detect faces and blur them. Returns a new bitmap with faces blurred.
     * Returns the original bitmap if no faces are found or detection fails.
     */
    suspend fun blurFaces(bitmap: Bitmap): Bitmap {
        return try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
            val detector = FaceDetection.getClient(options)

            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()

            if (faces.isEmpty()) {
                Log.d(TAG, "No faces found")
                return bitmap
            }

            Log.d(TAG, "Found ${faces.size} face(s), blurring...")
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            for (face in faces) {
                val bounds = face.boundingBox
                // Expand bounds slightly for better coverage
                val expanded = Rect(
                    (bounds.left - bounds.width() * 0.1f).toInt().coerceAtLeast(0),
                    (bounds.top - bounds.height() * 0.1f).toInt().coerceAtLeast(0),
                    (bounds.right + bounds.width() * 0.1f).toInt().coerceAtMost(result.width),
                    (bounds.bottom + bounds.height() * 0.1f).toInt().coerceAtMost(result.height)
                )
                pixelBlurRegion(result, expanded)
            }

            detector.close()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Face blur failed: ${e.message}")
            bitmap
        }
    }

    /**
     * Apply a heavy pixel-level box blur to a rectangular region of a mutable bitmap.
     * No RenderScript dependency — pure pixel manipulation.
     */
    private fun pixelBlurRegion(bitmap: Bitmap, rect: Rect) {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        val width = right - left
        val height = bottom - top

        if (width < 2 || height < 2) return

        // Extract pixels
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)

        // Pixelate: average blocks of ~12px
        val blockSize = maxOf(width, height) / 8
        if (blockSize < 2) return

        for (y in 0 until height step blockSize) {
            for (x in 0 until width step blockSize) {
                val bw = minOf(blockSize, width - x)
                val bh = minOf(blockSize, height - y)

                var r = 0L; var g = 0L; var b = 0L
                var count = 0

                for (dy in 0 until bh) {
                    for (dx in 0 until bw) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                        count++
                    }
                }

                if (count == 0) continue
                val avgColor = (0xFF shl 24) or
                    ((r / count).toInt() shl 16) or
                    ((g / count).toInt() shl 8) or
                    (b / count).toInt()

                for (dy in 0 until bh) {
                    for (dx in 0 until bw) {
                        pixels[(y + dy) * width + (x + dx)] = avgColor
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, left, top, width, height)
    }

    /**
     * Check if the bitmap has any faces.
     */
    suspend fun hasFaces(bitmap: Bitmap): Boolean {
        return try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
            val detector = FaceDetection.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            detector.close()
            faces.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
