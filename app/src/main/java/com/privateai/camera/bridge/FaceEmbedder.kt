package com.privateai.camera.bridge

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FaceEmbedder(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val detector: FaceDetector = FaceDetection.getClient()
    private val inputSize = 160  // FaceNet InceptionResnetV1 expects 160x160

    companion object {
        private const val TAG = "FaceEmbedder"
        private const val EMBEDDING_DIM = 512  // FaceNet outputs 512-dim embeddings
    }

    init {
        try {
            val modelBytes = context.assets.open("models/face_embed.onnx").readBytes()
            ortSession = ortEnv.createSession(modelBytes)
            Log.i(TAG, "Face embedding model loaded on CPU")

            ortSession?.let { session ->
                session.inputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Input: name=$name, info=$info")
                }
                session.outputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Output: name=$name, info=$info")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load face embedding model: ${e.message}")
        }
    }

    /**
     * Detect faces in a bitmap using ML Kit, crop each face, and return embeddings.
     * Returns list of (face bounding box as RectF normalized to 0-1, embedding FloatArray).
     */
    fun detectAndEmbed(bitmap: Bitmap): List<Pair<RectF, FloatArray>> {
        val session = ortSession ?: run {
            Log.e(TAG, "Model not loaded")
            return emptyList()
        }

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = Tasks.await(detector.process(inputImage))

            if (faces.isEmpty()) return emptyList()

            val bitmapWidth = bitmap.width
            val bitmapHeight = bitmap.height
            val results = mutableListOf<Pair<RectF, FloatArray>>()

            for (face in faces) {
                val bounds = face.boundingBox

                // Clamp bounding box to bitmap bounds
                val left = bounds.left.coerceIn(0, bitmapWidth - 1)
                val top = bounds.top.coerceIn(0, bitmapHeight - 1)
                val right = bounds.right.coerceIn(left + 1, bitmapWidth)
                val bottom = bounds.bottom.coerceIn(top + 1, bitmapHeight)
                val width = right - left
                val height = bottom - top

                if (width <= 0 || height <= 0) continue

                // Crop face from bitmap
                val faceBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
                val embedding = embed(faceBitmap)
                if (faceBitmap !== bitmap) faceBitmap.recycle()

                // Normalize bounding box to 0-1
                val normalizedBox = RectF(
                    left.toFloat() / bitmapWidth,
                    top.toFloat() / bitmapHeight,
                    right.toFloat() / bitmapWidth,
                    bottom.toFloat() / bitmapHeight
                )

                results.add(Pair(normalizedBox, embedding))
            }

            return results
        } catch (e: Exception) {
            Log.e(TAG, "Face detection and embedding failed", e)
            return emptyList()
        }
    }

    /**
     * Get embedding for a single pre-cropped face bitmap.
     */
    fun embed(faceBitmap: Bitmap): FloatArray {
        val session = ortSession ?: run {
            Log.e(TAG, "Model not loaded")
            return FloatArray(0)
        }

        try {
            val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
            val inputBuffer = preprocessBitmap(resized)
            if (resized !== faceBitmap) resized.recycle()

            val inputTensor = OnnxTensor.createTensor(
                ortEnv,
                inputBuffer,
                longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            )

            val results = session.run(mapOf("input" to inputTensor))
            inputTensor.close()

            val outputTensor = results[0] as? OnnxTensor
            if (outputTensor == null) {
                Log.e(TAG, "Output is not OnnxTensor")
                results.close()
                return FloatArray(0)
            }

            val rawOutput = (outputTensor.value as Array<FloatArray>)[0]
            results.close()

            // L2 normalize the embedding
            var norm = 0f
            for (v in rawOutput) norm += v * v
            norm = Math.sqrt(norm.toDouble()).toFloat()
            return if (norm > 0) FloatArray(rawOutput.size) { rawOutput[it] / norm } else rawOutput.copyOf()
        } catch (e: Exception) {
            Log.e(TAG, "Face embedding failed", e)
            return FloatArray(0)
        }
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

        // NCHW layout — FaceNet expects pixels in [-1, 1] range: (pixel/255 - 0.5) / 0.5
        // Channel 0: R
        for (i in 0 until channelSize) {
            val r = ((pixels[i] shr 16) and 0xFF) / 127.5f - 1.0f
            buffer.put(r)
        }
        // Channel 1: G
        for (i in 0 until channelSize) {
            val g = ((pixels[i] shr 8) and 0xFF) / 127.5f - 1.0f
            buffer.put(g)
        }
        // Channel 2: B
        for (i in 0 until channelSize) {
            val b = (pixels[i] and 0xFF) / 127.5f - 1.0f
            buffer.put(b)
        }

        buffer.rewind()
        return buffer
    }

    fun release() {
        ortSession?.close()
        ortSession = null
        detector.close()
    }
}
