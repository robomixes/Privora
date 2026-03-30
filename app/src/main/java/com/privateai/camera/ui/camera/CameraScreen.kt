package com.privateai.camera.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.privateai.camera.bridge.Detection
import com.privateai.camera.bridge.OnnxDetector
import com.privateai.camera.util.cropDetectionRegion
import com.privateai.camera.util.launchImageSearch
import java.util.concurrent.TimeUnit

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (hasCameraPermission) {
        CameraPreviewWithDetection()
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Private AI Camera needs camera access",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    "All processing happens on your device.\nNo data is sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Permission")
                }
            }
        }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun CameraPreviewWithDetection() {
    val context = LocalContext.current
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var inferenceTimeMs by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableLongStateOf(0L) }

    // Camera toggle
    val hasFrontCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    val cameraSelector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    // Focus & selection
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var meteringPointFactory by remember { mutableStateOf<PreviewView?>(null) }
    var selectedDetection by remember { mutableStateOf<Detection?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var isFrozen by remember { mutableStateOf(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Retain latest frame for image search cropping
    var latestFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Initialize detector
    val detector = remember { OnnxDetector(context) }

    DisposableEffect(Unit) {
        onDispose {
            detector.release()
            latestFrameBitmap?.recycle()
            frozenBitmap?.recycle()
        }
    }

    // Clear stale state on camera switch
    LaunchedEffect(useFrontCamera) {
        detections = emptyList()
        selectedDetection = null
        focusPoint = null
        isFrozen = false
        frozenBitmap?.recycle()
        frozenBitmap = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview with frame analysis
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = cameraSelector,
            onFrameAnalyzed = { bitmap ->
                // Skip processing when frozen (object selected)
                if (isFrozen) {
                    bitmap.recycle()
                    return@CameraPreview
                }

                frameCount++
                if (frameCount % 3 != 0L) {
                    bitmap.recycle()
                    return@CameraPreview
                }

                val start = System.currentTimeMillis()
                val results = detector.detect(bitmap)
                inferenceTimeMs = System.currentTimeMillis() - start
                detections = results

                // Keep latest frame for image search (copy for thread safety)
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                val old = latestFrameBitmap
                latestFrameBitmap = copy
                old?.recycle()
                bitmap.recycle()
            },
            onCameraBound = { camera, previewView ->
                cameraControl = camera.cameraControl
                meteringPointFactory = previewView
            }
        )

        // Frozen frame with blur + clear selected region
        if (isFrozen && frozenBitmap != null && selectedDetection != null) {
            val frozen = frozenBitmap!!
            val det = selectedDetection!!

            // Blurred frozen frame covers the live preview
            Image(
                bitmap = frozen.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )
            // Dark overlay on top of blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            // Clear (unblurred) crop of selected object on top
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenW = maxWidth
                val screenH = maxHeight

                // Add padding to detection box (15% expansion)
                val padX = (det.x2 - det.x1) * 0.15f
                val padY = (det.y2 - det.y1) * 0.15f
                val ex1 = (det.x1 - padX).coerceAtLeast(0f)
                val ey1 = (det.y1 - padY).coerceAtLeast(0f)
                val ex2 = (det.x2 + padX).coerceAtMost(1f)
                val ey2 = (det.y2 + padY).coerceAtMost(1f)

                // Crop from unblurred frozen bitmap
                val cropX = (ex1 * frozen.width).toInt().coerceIn(0, frozen.width - 1)
                val cropY = (ey1 * frozen.height).toInt().coerceIn(0, frozen.height - 1)
                val cropW = ((ex2 - ex1) * frozen.width).toInt().coerceIn(1, frozen.width - cropX)
                val cropH = ((ey2 - ey1) * frozen.height).toInt().coerceIn(1, frozen.height - cropY)

                val cropped = remember(det, frozen) {
                    Bitmap.createBitmap(frozen, cropX, cropY, cropW, cropH)
                }

                Image(
                    bitmap = cropped.asImageBitmap(),
                    contentDescription = "Selected: ${det.className}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(
                            x = screenW * ex1,
                            y = screenH * ey1
                        )
                        .width(screenW * (ex2 - ex1))
                        .height(screenH * (ey2 - ey1))
                )
            }
        }

        // Detection overlay — only show selected detection when frozen
        DetectionOverlay(
            detections = if (isFrozen && selectedDetection != null) {
                listOf(selectedDetection!!)
            } else {
                detections
            },
            selectedDetection = selectedDetection,
            isFrontCamera = useFrontCamera,
            onDetectionTapped = { detection, tapOffset ->
                selectedDetection = detection
                focusPoint = tapOffset
                isFrozen = true

                // Keep frozen frame (latest frame is already retained)
                frozenBitmap?.recycle()
                frozenBitmap = latestFrameBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                // Trigger autofocus at tap point
                val factory = meteringPointFactory?.meteringPointFactory ?: return@DetectionOverlay
                val control = cameraControl ?: return@DetectionOverlay
                try {
                    val meteringPoint = factory.createPoint(tapOffset.x, tapOffset.y)
                    val action = FocusMeteringAction.Builder(meteringPoint)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    control.startFocusAndMetering(action)
                } catch (_: Exception) { }
            },
            onBackgroundTapped = {
                selectedDetection = null
                isFrozen = false
                frozenBitmap?.recycle()
                frozenBitmap = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Focus ring animation
        focusPoint?.let { point ->
            FocusRingIndicator(
                center = point,
                onAnimationComplete = { focusPoint = null }
            )
        }

        // Top bar: object count + inference time + camera switch
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${detections.size} objects",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${inferenceTimeMs}ms",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                if (hasFrontCamera) {
                    IconButton(
                        onClick = {
                            useFrontCamera = !useFrontCamera
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Selected detection detail card
        selectedDetection?.let { det ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = det.className.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Confidence: ${(det.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Text search
                        TextButton(onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/search?q=${det.className}")
                            )
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Web Search", modifier = Modifier.padding(start = 4.dp))
                        }

                        // Image search
                        TextButton(onClick = {
                            val frame = latestFrameBitmap ?: return@TextButton
                            val cropped = cropDetectionRegion(frame, det)
                            launchImageSearch(context, cropped)
                            cropped.recycle()
                        }) {
                            Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Search by Image", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }

        // Bottom chips (only when no detection is selected)
        if (detections.isNotEmpty() && selectedDetection == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detections.take(3).forEach { detection ->
                    Row(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/search?q=${detection.className}")
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                        Text(
                            text = detection.className,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
