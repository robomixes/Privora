package com.privateai.camera.bridge

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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

    companion object {
        private const val TAG = "OnnxDetector"
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
        private const val NUM_CLASSES = 80
    }

    init {
        try {
            val modelBytes = context.assets.open("models/yolov8n.onnx").readBytes()
            ortSession = ortEnv.createSession(modelBytes)
            Log.i(TAG, "YOLOv8n model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val session = ortSession ?: return emptyList()

        // 1. Preprocess: resize to 640x640, normalize, NCHW layout
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = preprocessBitmap(resized)
        if (resized != bitmap) resized.recycle()

        // 2. Run inference
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            inputBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )

        val results = session.run(mapOf("images" to inputTensor))
        inputTensor.close()

        // 3. Post-process: parse output (1, 84, 8400)
        val outputTensor = results[0].value as Array<Array<FloatArray>>
        val output = outputTensor[0]  // shape: (84, 8400)
        results.close()

        // 4. Parse detections + NMS
        val rawDetections = parseOutput(output, bitmap.width, bitmap.height)
        return nms(rawDetections)
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val buffer = FloatBuffer.allocate(3 * inputSize * inputSize)
        val channelSize = inputSize * inputSize

        // NCHW layout: all R, then all G, then all B
        for (i in pixels.indices) {
            val pixel = pixels[i]
            buffer.put(i, ((pixel shr 16) and 0xFF) / 255.0f)                  // R
            buffer.put(i + channelSize, ((pixel shr 8) and 0xFF) / 255.0f)     // G
            buffer.put(i + 2 * channelSize, (pixel and 0xFF) / 255.0f)         // B
        }

        buffer.rewind()
        return buffer
    }

    private fun parseOutput(
        output: Array<FloatArray>,  // shape (84, 8400)
        origWidth: Int,
        origHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numBoxes = output[0].size  // 8400

        for (i in 0 until numBoxes) {
            // YOLOv8 output: rows 0-3 = cx, cy, w, h; rows 4-83 = class scores
            val cx = output[0][i]
            val cy = output[1][i]
            val w = output[2][i]
            val h = output[3][i]

            // Find best class
            var maxScore = 0f
            var bestClass = 0
            for (c in 0 until NUM_CLASSES) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    bestClass = c
                }
            }

            if (maxScore < CONFIDENCE_THRESHOLD) continue

            // Convert from cx,cy,w,h to x1,y1,x2,y2 (normalized 0-1)
            val x1 = max(0f, (cx - w / 2) / inputSize)
            val y1 = max(0f, (cy - h / 2) / inputSize)
            val x2 = min(1f, (cx + w / 2) / inputSize)
            val y2 = min(1f, (cy + h / 2) / inputSize)

            detections.add(
                Detection(
                    x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                    confidence = maxScore,
                    classId = bestClass,
                    className = COCO_CLASSES.getOrElse(bestClass) { "unknown" }
                )
            )
        }

        return detections
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best, it) > IOU_THRESHOLD }
        }

        return kept
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

    fun benchmark(): Long {
        val session = ortSession ?: return -1
        val buffer = FloatBuffer.allocate(3 * inputSize * inputSize)
        buffer.rewind()

        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            buffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )

        // Warmup
        session.run(mapOf("images" to inputTensor)).close()

        // Measure 5 runs
        val start = System.currentTimeMillis()
        repeat(5) {
            session.run(mapOf("images" to inputTensor)).close()
        }
        val elapsed = System.currentTimeMillis() - start
        inputTensor.close()

        return elapsed / 5
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
