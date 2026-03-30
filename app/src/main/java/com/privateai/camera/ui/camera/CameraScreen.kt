package com.privateai.camera.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.privateai.camera.bridge.Detection
import com.privateai.camera.bridge.OnnxDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    // Initialize detector
    val detector = remember { OnnxDetector(context) }

    DisposableEffect(Unit) {
        onDispose { detector.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview with frame analysis
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onFrameAnalyzed = { bitmap ->
                // Throttle: process every 3rd frame (~10fps from 30fps camera)
                frameCount++
                if (frameCount % 3 != 0L) {
                    bitmap.recycle()
                    return@CameraPreview
                }

                val start = System.currentTimeMillis()
                val results = detector.detect(bitmap)
                inferenceTimeMs = System.currentTimeMillis() - start
                detections = results
                bitmap.recycle()
            }
        )

        // Detection overlay
        DetectionOverlay(
            detections = detections,
            modifier = Modifier.fillMaxSize()
        )

        // Top info bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${detections.size} objects",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Text(
                text = "${inferenceTimeMs}ms",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom: detected objects as tappable chips (web search)
        if (detections.isNotEmpty()) {
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
