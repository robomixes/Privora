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
import kotlin.math.exp

class ImageClassifier(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val labels: List<String>
    private val inputSize = 224

    companion object {
        private const val TAG = "ImageClassifier"
        private const val NUM_CLASSES = 1000

        // ImageNet normalization constants
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    init {
        // Load labels — one per line, 1000 total
        labels = context.assets.open("imagenet_labels.txt").bufferedReader().useLines { lines ->
            lines.toList()
        }
        Log.i(TAG, "Loaded ${labels.size} labels")

        try {
            val modelBytes = context.assets.open("models/mobilenet_v3_small.onnx").readBytes()
            // Force CPU only — same as OnnxDetector
            ortSession = ortEnv.createSession(modelBytes)
            Log.i(TAG, "MobileNetV3-Small model loaded on CPU")

            ortSession?.let { session ->
                session.inputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Input: name=$name, info=$info")
                }
                session.outputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Output: name=$name, info=$info")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
        }
    }

    /**
     * Classify an image and return top-N labels with confidence.
     */
    fun classify(bitmap: Bitmap, topN: Int = 5, minConfidence: Float = 0.05f): List<Pair<String, Float>> {
        val session = ortSession ?: run {
            Log.e(TAG, "Model not loaded")
            return emptyList()
        }

        try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = preprocessBitmap(resized)
            if (resized !== bitmap) resized.recycle()

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
                return emptyList()
            }

            val rawOutput = (outputTensor.value as Array<FloatArray>)[0]
            results.close()

            // Apply softmax
            val probabilities = softmax(rawOutput)

            // Find top-N indices sorted by confidence descending
            val indexed = probabilities.mapIndexed { index, conf -> index to conf }
                .filter { it.second >= minConfidence }
                .sortedByDescending { it.second }
                .take(topN)

            return indexed.map { (index, conf) ->
                val label = labels.getOrElse(index) { "unknown" }
                label to conf
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            return emptyList()
        }
    }

    /**
     * Get the 576-dim embedding vector from the penultimate layer for similarity comparison.
     * Much better than classification logits for finding visually similar images.
     */
    fun getFeatureVector(bitmap: Bitmap): FloatArray {
        val session = ortSession ?: run {
            Log.e(TAG, "Model not loaded")
            return FloatArray(0)
        }

        try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = preprocessBitmap(resized)
            if (resized !== bitmap) resized.recycle()

            val inputTensor = OnnxTensor.createTensor(
                ortEnv,
                inputBuffer,
                longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            )

            val results = session.run(mapOf("input" to inputTensor))
            inputTensor.close()

            // Second output is the 576-dim embedding from penultimate layer
            val embeddingTensor = results[1] as? OnnxTensor
            if (embeddingTensor == null) {
                // Fallback to first output (logits) if model only has one output
                val logitsTensor = results[0] as? OnnxTensor
                val output = if (logitsTensor != null) (logitsTensor.value as Array<FloatArray>)[0].copyOf() else FloatArray(0)
                results.close()
                return output
            }

            val embedding = (embeddingTensor.value as Array<FloatArray>)[0]
            results.close()

            // L2 normalize for cosine similarity
            var norm = 0f
            for (v in embedding) norm += v * v
            norm = Math.sqrt(norm.toDouble()).toFloat()
            return if (norm > 0) FloatArray(embedding.size) { embedding[it] / norm } else embedding.copyOf()
        } catch (e: Exception) {
            Log.e(TAG, "Feature extraction failed", e)
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

        // NCHW layout with ImageNet normalization: (pixel/255 - mean) / std
        // Channel 0: R
        for (i in 0 until channelSize) {
            val r = ((pixels[i] shr 16) and 0xFF) / 255.0f
            buffer.put((r - MEAN[0]) / STD[0])
        }
        // Channel 1: G
        for (i in 0 until channelSize) {
            val g = ((pixels[i] shr 8) and 0xFF) / 255.0f
            buffer.put((g - MEAN[1]) / STD[1])
        }
        // Channel 2: B
        for (i in 0 until channelSize) {
            val b = (pixels[i] and 0xFF) / 255.0f
            buffer.put((b - MEAN[2]) / STD[2])
        }

        buffer.rewind()
        return buffer
    }

    private fun softmax(logits: FloatArray): FloatArray {
        // Subtract max for numerical stability
        val maxVal = logits.max()
        val exps = FloatArray(logits.size) { exp(logits[it] - maxVal) }
        val sum = exps.sum()
        return FloatArray(logits.size) { exps[it] / sum }
    }

    fun release() {
        ortSession?.close()
        ortSession = null
    }
}
