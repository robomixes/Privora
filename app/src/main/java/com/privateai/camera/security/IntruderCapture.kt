// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Silently captures a front-camera photo when someone enters the wrong PIN.
 *
 * Uses Camera2 API directly (no CameraX lifecycle needed) — reliable for
 * background captures without a preview surface.
 *
 * - Photos stored in `vault/intruder/` (plain JPEG when vault locked, encrypted when unlocked)
 * - Toggle in SharedPreferences (default off)
 * - Auto-deletes captures older than 30 days
 */
object IntruderCapture {

    private const val TAG = "IntruderCapture"
    private const val PREFS_NAME = "intruder_settings"
    private const val KEY_ENABLED = "intruder_enabled"
    private const val DIR_NAME = "intruder"
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Fire-and-forget: silently capture a front-camera photo if the feature is enabled. */
    fun snapIfEnabled(context: Context) {
        if (!isEnabled(context)) return
        try {
            snapWithCamera2(context)
        } catch (e: Exception) {
            Log.e(TAG, "Intruder capture failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission") // CAMERA permission already declared + granted for main camera
    private fun snapWithCamera2(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val mainHandler = Handler(Looper.getMainLooper())

        // Find front camera
        val frontId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
        if (frontId == null) {
            Log.w(TAG, "No front camera found")
            return
        }

        val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    reader.close()
                    saveCapturedBytes(context, bytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read image: ${e.message}")
                    try { image.close() } catch (_: Exception) {}
                    try { reader.close() } catch (_: Exception) {}
                }
            }
        }, mainHandler)

        cameraManager.openCamera(frontId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                try {
                    camera.createCaptureSession(
                        listOf(imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(imageReader.surface)
                                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                        set(CaptureRequest.JPEG_QUALITY, 60)
                                    }.build()
                                    session.capture(request, object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            result: android.hardware.camera2.TotalCaptureResult
                                        ) {
                                            Log.d(TAG, "Capture completed")
                                            try { session.close() } catch (_: Exception) {}
                                            camera.close()
                                        }
                                    }, mainHandler)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Capture request failed: ${e.message}")
                                    camera.close()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Session configure failed")
                                camera.close()
                            }
                        },
                        mainHandler
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Create session failed: ${e.message}")
                    camera.close()
                }
            }

            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
                camera.close()
            }
        }, mainHandler)
    }

    /** Save JPEG bytes to the intruder dir. */
    private fun saveCapturedBytes(context: Context, jpegBytes: ByteArray) {
        val dir = getIntruderDir(context)
        val timestamp = System.currentTimeMillis()
        val id = "$timestamp"

        try {
            val crypto = CryptoManager(context).also { it.initialize() }
            if (crypto.isUnlocked()) {
                crypto.encryptToFile(jpegBytes, File(dir, "$id.intruder.enc"))
                Log.i(TAG, "Intruder photo saved (encrypted): $id (${jpegBytes.size / 1024}KB)")
            } else {
                File(dir, "$id.intruder.jpg").writeBytes(jpegBytes)
                Log.i(TAG, "Intruder photo saved (plain, vault locked): $id (${jpegBytes.size / 1024}KB)")
            }
        } catch (e: Exception) {
            File(dir, "$id.intruder.jpg").writeBytes(jpegBytes)
            Log.i(TAG, "Intruder photo saved (plain, crypto error): $id (${jpegBytes.size / 1024}KB)")
        }

        cleanupOld(dir)
    }

    private fun cleanupOld(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.forEach { file ->
            val ts = file.name.substringBefore(".intruder").toLongOrNull() ?: return@forEach
            if (ts < cutoff) {
                file.delete()
                Log.d(TAG, "Deleted old intruder photo: ${file.name}")
            }
        }
    }

    // ── Public API for Settings viewer ─────────────────────────────

    private fun getIntruderDir(context: Context): File =
        File(context.filesDir, "vault/$DIR_NAME").also { it.mkdirs() }

    fun listCaptures(context: Context): List<IntruderEntry> {
        val dir = getIntruderDir(context)
        return (dir.listFiles() ?: emptyArray())
            .filter { it.name.contains(".intruder.") }
            .mapNotNull { file ->
                val ts = file.name.substringBefore(".intruder").toLongOrNull() ?: return@mapNotNull null
                val encrypted = file.name.endsWith(".enc")
                IntruderEntry(ts, file, encrypted)
            }
            .sortedByDescending { it.timestamp }
    }

    fun decryptCapture(context: Context, entry: IntruderEntry): Bitmap? {
        return try {
            val bytes = if (entry.encrypted) {
                val crypto = CryptoManager(context).also { it.initialize() }
                if (!crypto.isUnlocked()) return null
                crypto.decryptFile(entry.file)
            } else {
                entry.file.readBytes()
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load intruder photo: ${e.message}")
            null
        }
    }

    fun deleteCapture(entry: IntruderEntry) { entry.file.delete() }

    fun clearAll(context: Context) {
        getIntruderDir(context).listFiles()?.forEach { it.delete() }
    }
}

data class IntruderEntry(val timestamp: Long, val file: File, val encrypted: Boolean = true)
