package com.privateai.camera.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
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
    isVideoMode: Boolean = false,
    onFrameAnalyzed: ((Bitmap) -> Unit)? = null,
    onVideoCaptureReady: ((VideoCapture<Recorder>?) -> Unit)? = null,
    onCameraBound: ((Camera, PreviewView) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    DisposableEffect(lifecycleOwner, cameraSelector, isVideoMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            // Always create VideoCapture for long-press recording support
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)

            if (isVideoMode) {
                // Video mode: Preview + VideoCapture only (no ImageAnalysis)
                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, videoCapture
                    )
                    onVideoCaptureReady?.invoke(videoCapture)
                    onCameraBound?.invoke(camera, previewView)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Video mode bind failed: ${e.message}")
                }
            } else {
                // Photo mode: try Preview + ImageAnalysis + VideoCapture (for hold-to-record)
                val imageAnalysis = if (onFrameAnalyzed != null) {
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build().also { analysis ->
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val bitmap = imageProxyToBitmap(imageProxy)
                                if (bitmap != null) {
                                    onFrameAnalyzed(bitmap)
                                }
                                imageProxy.close()
                            }
                        }
                } else null

                try {
                    cameraProvider.unbindAll()
                    // Try all 3: Preview + ImageAnalysis + VideoCapture
                    val useCases = listOfNotNull(preview, imageAnalysis, videoCapture)
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, *useCases.toTypedArray()
                    )
                    onVideoCaptureReady?.invoke(videoCapture)
                    onCameraBound?.invoke(camera, previewView)
                    Log.d("CameraPreview", "Bound all 3 use cases (hold-to-record available)")
                } catch (e: Exception) {
                    // Fallback: Preview + ImageAnalysis only (device doesn't support 3 use cases)
                    Log.w("CameraPreview", "3 use cases failed, falling back: ${e.message}")
                    try {
                        cameraProvider.unbindAll()
                        val fallbackCases = listOfNotNull(preview, imageAnalysis)
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, *fallbackCases.toTypedArray()
                        )
                        onVideoCaptureReady?.invoke(null) // no video in photo mode on this device
                        onCameraBound?.invoke(camera, previewView)
                    } catch (e2: Exception) {
                        Log.e("CameraPreview", "Fallback bind also failed: ${e2.message}")
                    }
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
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
        Log.e("CameraPreview", "Failed to convert frame: ${e.message}")
        null
    }
}
