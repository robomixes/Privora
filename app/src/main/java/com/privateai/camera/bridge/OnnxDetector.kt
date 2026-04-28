// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val x1: Float, val y1: Float,  // top-left (normalized 0-1)
    val x2: Float, val y2: Float,  // bottom-right (normalized 0-1)
    val confidence: Float,
    val classId: Int,
    val className: String
)

class OnnxDetector(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val inputSize = 640
    var debugInfo: String = "loading..."
        private set

    companion object {
        private const val TAG = "OnnxDetector"
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
        private const val NUM_CLASSES = 80
        private const val NUM_BOXES = 8400
    }

    private var outputName: String = "output0"

    init {
        try {
            val modelBytes = context.assets.open("models/yolov8n.onnx").readBytes()
            // Force CPU only — NNAPI can produce incorrect results on some devices
            ortSession = ortEnv.createSession(modelBytes)
            Log.i(TAG, "YOLOv8n model loaded on CPU")

            // Get actual output name from model
            ortSession?.let { session ->
                session.outputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Output: name=$name, info=$info")
                    outputName = name
                }
                session.inputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Input: name=$name, info=$info")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
        }
    }

    // Letterbox padding info — needed to map detections back to original image
    private var padLeft = 0f
    private var padTop = 0f
    private var scale = 1f

    fun detect(bitmap: Bitmap, categoryFilter: Set<Int>? = null, minConfidence: Float = 0f): List<Detection> {
        val session = ortSession
        if (session == null) {
            debugInfo = "ERROR: model not loaded"
            return emptyList()
        }

        try {
            var results = detectInternal(session, bitmap)
            if (categoryFilter != null && categoryFilter.size < 80) {
                results = results.filter { it.classId in categoryFilter }
            }
            if (minConfidence > 0f) {
                results = results.filter { it.confidence >= minConfidence }
            }
            return results
        } catch (e: Exception) {
            debugInfo = "CRASH: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "Detection failed", e)
            return emptyList()
        }
    }

    private fun detectInternal(session: OrtSession, bitmap: Bitmap): List<Detection> {
        debugInfo = "running... ${bitmap.width}x${bitmap.height}"

        // 1. Preprocess: letterbox to 640x640 (no stretching, gray padding)
        val letterboxed = letterbox(bitmap)
        val inputBuffer = preprocessBitmap(letterboxed)
        letterboxed.recycle()

        // 2. Run inference
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            inputBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )

        val results = session.run(mapOf("images" to inputTensor))
        inputTensor.close()

        // 3. Get output tensor — use index 0
        debugInfo = "inference done, reading output..."
        val outputResult = results[0]
        val outputTensor = outputResult as? OnnxTensor
        if (outputTensor == null) {
            debugInfo = "ERROR: output not OnnxTensor, type=${outputResult?.javaClass?.name}"
            results.close()
            return emptyList()
        }
        val shape = outputTensor.info.shape
        debugInfo = "shape=${shape.toList()}"

        val rawValue = outputTensor.value

        // Extract to flat 2D: we need (84, 8400) regardless of input layout
        @Suppress("UNCHECKED_CAST")
        val arr3d = rawValue as Array<Array<FloatArray>>
        val batch = arr3d[0]

        // Determine layout from shape
        val transposed = shape[1] > shape[2]

        debugInfo = "shape=${shape.toList()} out=$outputName CPU-only"
        Log.d(TAG, debugInfo)

        // Find and log the highest scoring detection for debugging
        if (!transposed && batch.size >= 84) {
            var bestScore = 0f
            var bestClass = -1
            var bestIdx = -1
            val numBoxes = batch[0].size
            for (i in 0 until numBoxes) {
                for (c in 0 until NUM_CLASSES) {
                    val s = batch[4 + c][i]
                    if (s > bestScore) {
                        bestScore = s
                        bestClass = c
                        bestIdx = i
                    }
                }
            }
            val bestName = COCO_CLASSES.getOrElse(bestClass) { "?" }
            debugInfo += "\nbest: $bestName ${(bestScore * 100).toInt()}% idx=$bestIdx"
            if (bestIdx >= 0) {
                val cx = batch[0][bestIdx]
                val cy = batch[1][bestIdx]
                val w = batch[2][bestIdx]
                val h = batch[3][bestIdx]
                debugInfo += "\ncx=${cx.toInt()} cy=${cy.toInt()} w=${w.toInt()} h=${h.toInt()}"
            }
            Log.d(TAG, debugInfo)
        }

        val rawDetections = if (transposed) {
            // (8400, 84): each row is a box with [cx, cy, w, h, cls0, cls1, ..., cls79]
            parseOutputTransposed(batch)
        } else {
            // (84, 8400): each row is an attribute across all boxes
            parseOutputNormal(batch)
        }

        results.close()

        return nms(rawDetections)
    }

    private fun letterbox(bitmap: Bitmap): Bitmap {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()

        // Scale to fit within inputSize x inputSize, preserving aspect ratio
        scale = min(inputSize / srcW, inputSize / srcH)
        val newW = (srcW * scale).toInt()
        val newH = (srcH * scale).toInt()

        padLeft = (inputSize - newW) / 2f
        padTop = (inputSize - newH) / 2f

        // Create gray canvas
        val result = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(0xFF808080.toInt())  // gray fill (114,114,114 is YOLOv8 standard but gray works)

        // Draw scaled image centered
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        canvas.drawBitmap(scaled, padLeft, padTop, null)
        if (scaled !== bitmap) scaled.recycle() // Don't recycle original if no scaling needed

        return result
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val channelSize = inputSize * inputSize
        val totalSize = 3 * channelSize

        // Use a DIRECT ByteBuffer — required by ONNX Runtime for zero-copy
        val byteBuffer = ByteBuffer.allocateDirect(totalSize * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val buffer = byteBuffer.asFloatBuffer()

        // NCHW layout: all R values, then all G values, then all B values
        // Write sequentially (not indexed) to avoid FloatBuffer position issues
        // Channel 0: R
        for (i in 0 until channelSize) {
            buffer.put(((pixels[i] shr 16) and 0xFF) / 255.0f)
        }
        // Channel 1: G
        for (i in 0 until channelSize) {
            buffer.put(((pixels[i] shr 8) and 0xFF) / 255.0f)
        }
        // Channel 2: B
        for (i in 0 until channelSize) {
            buffer.put((pixels[i] and 0xFF) / 255.0f)
        }

        buffer.rewind()
        return buffer
    }

    // Layout: (84, 8400) — each row is an attribute, columns are boxes
    private fun parseOutputNormal(batch: Array<FloatArray>): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numBoxes = batch[0].size

        Log.d(TAG, "parseOutputNormal: ${batch.size} rows x $numBoxes cols")

        for (i in 0 until numBoxes) {
            val cx = batch[0][i]
            val cy = batch[1][i]
            val w = batch[2][i]
            val h = batch[3][i]

            var maxScore = 0f
            var bestClass = 0
            for (c in 0 until NUM_CLASSES) {
                val score = batch[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    bestClass = c
                }
            }

            if (maxScore < CONFIDENCE_THRESHOLD) continue

            val det = boxToDetection(cx, cy, w, h, maxScore, bestClass)
            if (det != null) detections.add(det)
        }

        // Log first few detections for debugging
        detections.take(3).forEach {
            Log.d(TAG, "Detection: ${it.className} ${(it.confidence * 100).toInt()}% box=(${it.x1},${it.y1},${it.x2},${it.y2})")
        }

        return detections
    }

    // Layout: (8400, 84) — each row is a box with [cx, cy, w, h, cls0..cls79]
    private fun parseOutputTransposed(batch: Array<FloatArray>): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numBoxes = batch.size

        Log.d(TAG, "parseOutputTransposed: $numBoxes boxes x ${batch[0].size} attrs")

        for (i in 0 until numBoxes) {
            val box = batch[i]
            val cx = box[0]
            val cy = box[1]
            val w = box[2]
            val h = box[3]

            var maxScore = 0f
            var bestClass = 0
            for (c in 0 until NUM_CLASSES) {
                val score = box[4 + c]
                if (score > maxScore) {
                    maxScore = score
                    bestClass = c
                }
            }

            if (maxScore < CONFIDENCE_THRESHOLD) continue

            val det = boxToDetection(cx, cy, w, h, maxScore, bestClass)
            if (det != null) detections.add(det)
        }

        detections.take(3).forEach {
            Log.d(TAG, "Detection: ${it.className} ${(it.confidence * 100).toInt()}% box=(${it.x1},${it.y1},${it.x2},${it.y2})")
        }

        return detections
    }

    private fun boxToDetection(cx: Float, cy: Float, w: Float, h: Float, score: Float, classId: Int): Detection? {
        // cx,cy,w,h are in letterboxed pixel space (0-640)
        // Remove letterbox padding, then normalize to 0-1 relative to original image

        // Convert to box corners in letterboxed space
        val lx1 = cx - w / 2f
        val ly1 = cy - h / 2f
        val lx2 = cx + w / 2f
        val ly2 = cy + h / 2f

        // Expand box by 10% to better cover object edges
        val expandX = w * 0.10f
        val expandY = h * 0.10f
        val elx1 = lx1 - expandX
        val ely1 = ly1 - expandY
        val elx2 = lx2 + expandX
        val ely2 = ly2 + expandY

        // Remove padding and scale back to original image (normalized 0-1)
        val x1 = max(0f, (elx1 - padLeft) / (inputSize - 2 * padLeft))
        val y1 = max(0f, (ely1 - padTop) / (inputSize - 2 * padTop))
        val x2 = min(1f, (elx2 - padLeft) / (inputSize - 2 * padLeft))
        val y2 = min(1f, (ely2 - padTop) / (inputSize - 2 * padTop))

        // Skip tiny or full-frame boxes
        val bw = x2 - x1
        val bh = y2 - y1
        if (bw < 0.02f || bh < 0.02f) return null
        if (bw > 0.95f && bh > 0.95f) return null

        return Detection(
            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
            confidence = score,
            classId = classId,
            className = COCO_CLASSES.getOrElse(classId) { "unknown" }
        )
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        // Group by class for per-class NMS
        val byClass = detections.groupBy { it.classId }
        val kept = mutableListOf<Detection>()

        for ((_, classDetections) in byClass) {
            val sorted = classDetections.sortedByDescending { it.confidence }.toMutableList()
            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                kept.add(best)
                sorted.removeAll { iou(best, it) > IOU_THRESHOLD }
            }
        }

        return kept.sortedByDescending { it.confidence }
    }

    private fun iou(a: Detection, b: Detection): Float {
        val interX1 = max(a.x1, b.x1)
        val interY1 = max(a.y1, b.y1)
        val interX2 = min(a.x2, b.x2)
        val interY2 = min(a.y2, b.y2)

        val interArea = max(0f, interX2 - interX1) * max(0f, interY2 - interY1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    fun release() {
        ortSession?.close()
        ortSession = null
    }
}

val COCO_CLASSES = listOf(
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
    "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
    "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
    "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
    "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
    "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
    "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
    "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
    "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
    "toothbrush"
)
