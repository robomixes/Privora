package com.privateai.camera.service

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.privateai.camera.bridge.OnnxDetector

private const val PREFS_NAME = "device_profile"
private const val KEY_TIER = "tier"
private const val KEY_INFERENCE_MS = "inference_ms"
private const val KEY_RAM_MB = "ram_mb"
private const val KEY_PROFILED = "profiled"

enum class DeviceTier { HIGH, MEDIUM, LOW }

data class DeviceProfile(
    val tier: DeviceTier,
    val inferenceMs: Long,
    val ramMb: Long,
    val cpuCores: Int
)

object DeviceProfiler {

    private const val TAG = "DeviceProfiler"

    fun isProfiled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROFILED, false)
    }

    fun getProfile(context: Context): DeviceProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DeviceProfile(
            tier = DeviceTier.valueOf(prefs.getString(KEY_TIER, null) ?: DeviceTier.MEDIUM.name),
            inferenceMs = prefs.getLong(KEY_INFERENCE_MS, 200),
            ramMb = prefs.getLong(KEY_RAM_MB, 4096),
            cpuCores = Runtime.getRuntime().availableProcessors()
        )
    }

    /**
     * Run benchmark: load YOLOv8n and measure inference time.
     * Classifies device into HIGH/MEDIUM/LOW tier.
     */
    fun runBenchmark(context: Context): DeviceProfile {
        Log.i(TAG, "Starting device benchmark...")

        val ramMb = getDeviceRamMb(context)
        val cpuCores = Runtime.getRuntime().availableProcessors()

        // Benchmark ONNX inference
        var inferenceMs = 999L
        try {
            val detector = OnnxDetector(context)
            // Warmup
            val testBitmap = android.graphics.Bitmap.createBitmap(640, 480, android.graphics.Bitmap.Config.ARGB_8888)
            detector.detect(testBitmap)

            // Measure 3 runs
            val start = System.currentTimeMillis()
            repeat(3) { detector.detect(testBitmap) }
            inferenceMs = (System.currentTimeMillis() - start) / 3

            testBitmap.recycle()
            detector.release()
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark failed: ${e.message}")
        }

        val tier = when {
            inferenceMs < 300 && ramMb > 4096 -> DeviceTier.HIGH
            inferenceMs < 500 && ramMb > 2048 -> DeviceTier.MEDIUM
            else -> DeviceTier.LOW
        }

        val profile = DeviceProfile(tier, inferenceMs, ramMb, cpuCores)

        // Save
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PROFILED, true)
            .putString(KEY_TIER, tier.name)
            .putLong(KEY_INFERENCE_MS, inferenceMs)
            .putLong(KEY_RAM_MB, ramMb)
            .apply()

        Log.i(TAG, "Benchmark complete: $profile")
        return profile
    }

    fun clearProfile(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun getDeviceRamMb(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    /**
     * How many frames to skip between ONNX detections. Lower = faster but heavier.
     */
    fun getDetectionFrameSkip(context: Context): Int {
        return when (getProfile(context).tier) {
            DeviceTier.HIGH -> 1   // every frame
            DeviceTier.MEDIUM -> 3 // every 3rd frame
            DeviceTier.LOW -> 5    // every 5th frame
        }
    }

    /**
     * Whether face count should run in the given camera mode.
     */
    fun isFaceCountEnabled(context: Context, isVideoMode: Boolean): Boolean {
        return when (getProfile(context).tier) {
            DeviceTier.HIGH -> true
            DeviceTier.MEDIUM -> !isVideoMode // photo only
            DeviceTier.LOW -> false
        }
    }

    /**
     * Whether a feature is limited (not disabled) on this device tier.
     * Used to show warning badges on the home screen.
     */
    fun isFeatureLimited(context: Context, feature: String): Boolean {
        val tier = getProfile(context).tier
        if (tier == DeviceTier.HIGH) return false
        return when (feature) {
            "detect" -> tier == DeviceTier.LOW
            "translate" -> tier == DeviceTier.LOW
            else -> false
        }
    }

    fun getTierDescription(tier: DeviceTier): String {
        return when (tier) {
            DeviceTier.HIGH -> "High Performance — All features at full speed"
            DeviceTier.MEDIUM -> "Medium — Face count disabled in video mode"
            DeviceTier.LOW -> "Low — Detection throttled, face count disabled, translation may be slow"
        }
    }
}
