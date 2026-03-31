package com.privateai.camera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun CaptureScreen(onBack: () -> Unit, onPhotoTap: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val hasFrontCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    val cameraSelector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    // Use a lock object for thread-safe bitmap access
    val frameLock = remember { Object() }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var lastThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var lastPhotoPath by remember { mutableStateOf<String?>(null) }
    var photoCount by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var showFlash by remember { mutableStateOf(false) }
    var frameCount by remember { mutableIntStateOf(0) }

    // Shutter button color animation
    val shutterColor by animateColorAsState(
        targetValue = if (isCapturing) Color.Gray else Color.White,
        animationSpec = tween(150),
        label = "shutter"
    )

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Camera Permission")
            }
        }
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        return
    }

    fun capturePhoto() {
        if (isCapturing) return
        // Take a safe copy of the current frame
        val bitmap: Bitmap
        synchronized(frameLock) {
            val frame = latestFrame ?: return
            bitmap = frame.copy(Bitmap.Config.ARGB_8888, false)
        }

        isCapturing = true
        showFlash = true

        scope.launch {
            // Generate thumbnail
            val thumbSize = 120
            val scale = thumbSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val thumb = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
            lastThumbnail?.recycle()
            lastThumbnail = thumb

            withContext(Dispatchers.IO) {
                try {
                    val photosDir = File(context.filesDir, "photos")
                    photosDir.mkdirs()
                    val file = File(photosDir, "photo_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    bitmap.recycle()
                    lastPhotoPath = file.absolutePath
                    photoCount++
                } catch (e: Exception) {
                    bitmap.recycle()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            delay(100)
            showFlash = false
            delay(50)
            isCapturing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = cameraSelector,
            onFrameAnalyzed = { bitmap ->
                // Only keep every 5th frame to reduce memory pressure
                frameCount++
                if (frameCount % 5 != 0) {
                    bitmap.recycle()
                    return@CameraPreview
                }
                synchronized(frameLock) {
                    latestFrame?.recycle()
                    latestFrame = bitmap
                }
            }
        )

        // White flash overlay on capture
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.6f))
            )
        }

        // Top bar: back + camera switch
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            if (hasFrontCamera) {
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
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

        // Bottom bar: thumbnail + shutter + photo count
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Last photo thumbnail — tap to view
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .clickable(enabled = lastPhotoPath != null) {
                        lastPhotoPath?.let { path -> onPhotoTap?.invoke(path) }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (lastThumbnail != null) {
                    Image(
                        bitmap = lastThumbnail!!.asImageBitmap(),
                        contentDescription = "Last photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            // Shutter button — outer ring + inner circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clickable(enabled = !isCapturing) { capturePhoto() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(shutterColor, CircleShape)
                )
            }

            // Photo count
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (photoCount > 0) {
                    Text(
                        text = "$photoCount",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
