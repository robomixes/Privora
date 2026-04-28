// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.util

import android.graphics.Bitmap
import kotlin.math.min

object BlurDetector {

    /**
     * Compute blur score. Higher = sharper. Typical: <100 = blurry, >300 = sharp.
     */
    fun getBlurScore(bitmap: Bitmap): Double {
        // 1. Scale down to max 200px on the longest side for speed
        val maxDim = 200
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }

        val w = scaled.width
        val h = scaled.height

        // 2. Extract pixels and convert to grayscale
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        if (scaled !== bitmap) scaled.recycle()

        val gray = DoubleArray(w * h)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }

        // 3. Apply 3x3 Laplacian kernel: [[0,1,0],[1,-4,1],[0,1,0]]
        //    For each pixel (except borders):
        //    lap = -4*center + top + bottom + left + right
        val lapCount = (w - 2) * (h - 2)
        if (lapCount <= 0) return 0.0

        val laplacian = DoubleArray(lapCount)
        var idx = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val center = gray[y * w + x]
                val top = gray[(y - 1) * w + x]
                val bottom = gray[(y + 1) * w + x]
                val left = gray[y * w + (x - 1)]
                val right = gray[y * w + (x + 1)]
                laplacian[idx++] = -4.0 * center + top + bottom + left + right
            }
        }

        // 4. Compute variance of Laplacian values
        var sum = 0.0
        for (i in 0 until lapCount) {
            sum += laplacian[i]
        }
        val mean = sum / lapCount

        var varianceSum = 0.0
        for (i in 0 until lapCount) {
            val diff = laplacian[i] - mean
            varianceSum += diff * diff
        }

        // 5. Return variance (higher = more edges = sharper)
        return varianceSum / lapCount
    }

    fun isBlurry(bitmap: Bitmap, threshold: Double = 100.0): Boolean {
        return getBlurScore(bitmap) < threshold
    }
}
