package com.privateai.camera.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    onFrameAnalyzed: ((Bitmap) -> Unit)? = null,
    onCameraBound: ((Camera, PreviewView) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Executor cleanup on composable disposal
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    // Camera binding — re-runs when cameraSelector changes (front/back toggle)
    DisposableEffect(lifecycleOwner, cameraSelector) {
        var orientationListener: OrientationEventListener? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)

            if (onFrameAnalyzed != null) {
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                // Track device orientation so rotationDegrees is always correct
                // This ensures detection works regardless of how the phone is held
                orientationListener = object : OrientationEventListener(context) {
                    override fun onOrientationChanged(orientation: Int) {
                        if (orientation == ORIENTATION_UNKNOWN) return
                        val rotation = when {
                            orientation in 45..134 -> Surface.ROTATION_270
                            orientation in 135..224 -> Surface.ROTATION_180
                            orientation in 225..314 -> Surface.ROTATION_90
                            else -> Surface.ROTATION_0
                        }
                        imageAnalysis.targetRotation = rotation
                    }
                }
                orientationListener.enable()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val bitmap = imageProxyToBitmap(imageProxy)
                    if (bitmap != null) {
                        onFrameAnalyzed(bitmap)
                    }
                    imageProxy.close()
                }
                useCases.add(imageAnalysis)
            }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )
                onCameraBound?.invoke(camera, previewView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            orientationListener?.disable()
            cameraProviderFuture.get().unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

private fun imageProxyToBitmap(imageProxy: androidx.camera.core.ImageProxy): Bitmap? {
    return try {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height

        val bitmap = Bitmap.createBitmap(
            rowStride / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        val cropped = if (rowStride / pixelStride != width) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height).also { bitmap.recycle() }
        } else {
            bitmap
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            cropped.recycle()
            rotated
        } else {
            cropped
        }
    } catch (e: Exception) {
        android.util.Log.e("CameraPreview", "Failed to convert frame: ${e.message}")
        null
    }
}
