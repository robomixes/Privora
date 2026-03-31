package com.privateai.camera.ui.qrscanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }

    var scannedResult by remember { mutableStateOf<ScannedCode?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var scanHistory by remember { mutableStateOf<List<ScannedCode>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Camera access needed to scan codes")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
            }
        }
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        return
    }

    fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Exception) {}
    }

    // Result bottom sheet
    if (showResult && scannedResult != null) {
        ModalBottomSheet(
            onDismissRequest = { showResult = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            ScannedResultContent(
                code = scannedResult!!,
                context = context,
                onDismiss = { showResult = false }
            )
        }
    }

    // History bottom sheet
    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Scan History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                if (scanHistory.isEmpty()) {
                    Text("No scans yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(scanHistory) { code ->
                            Card(Modifier.fillMaxWidth().clickable {
                                scannedResult = code
                                showHistory = false
                                showResult = true
                            }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(code.typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(code.displayValue, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Camera preview with barcode analysis
        val previewView = remember { PreviewView(context) }
        val executor = remember { Executors.newSingleThreadExecutor() }
        val scanner = remember { BarcodeScanning.getClient() }

        DisposableEffect(lifecycleOwner) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !showResult) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    if (barcodes.isNotEmpty() && !showResult) {
                                        val barcode = barcodes[0]
                                        val code = ScannedCode(
                                            rawValue = barcode.rawValue ?: "",
                                            displayValue = barcode.displayValue ?: barcode.rawValue ?: "",
                                            format = barcode.format,
                                            valueType = barcode.valueType,
                                            typeLabel = getTypeLabel(barcode.valueType)
                                        )
                                        scannedResult = code
                                        showResult = true
                                        scanHistory = (listOf(code) + scanHistory).take(50)
                                        try { vibrate() } catch (_: Exception) {}
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    } catch (_: Exception) {
                        imageProxy.close()
                    }
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                cameraControl = camera.cameraControl
                } catch (e: Exception) {
                    android.util.Log.e("QrScanner", "Camera setup failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                try { cameraProviderFuture.get().unbindAll() } catch (_: Exception) {}
                executor.shutdown()
            }
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Scan overlay — 4 dark bars around a transparent center (no BlendMode.Clear needed)
        Canvas(Modifier.fillMaxSize()) {
            val scanBoxSize = size.width * 0.7f
            val left = (size.width - scanBoxSize) / 2
            val top = (size.height - scanBoxSize) / 2
            val right = left + scanBoxSize
            val bottom = top + scanBoxSize
            val dimColor = Color.Black.copy(alpha = 0.5f)

            // Top bar
            drawRect(dimColor, topLeft = Offset.Zero, size = Size(size.width, top))
            // Bottom bar
            drawRect(dimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
            // Left bar
            drawRect(dimColor, topLeft = Offset(0f, top), size = Size(left, scanBoxSize))
            // Right bar
            drawRect(dimColor, topLeft = Offset(right, top), size = Size(size.width - right, scanBoxSize))

            // White border for scan area
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(scanBoxSize, scanBoxSize),
                cornerRadius = CornerRadius(16f),
                style = Stroke(width = 3f)
            )

            // Corner accents
            val cornerLen = 30f
            val cornerWidth = 4f
            val accentColor = Color(0xFF4CAF50)
            // Top-left
            drawLine(accentColor, Offset(left, top + 8), Offset(left, top + cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left + 8, top), Offset(left + cornerLen, top), strokeWidth = cornerWidth)
            // Top-right
            drawLine(accentColor, Offset(right, top + 8), Offset(right, top + cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right - 8, top), Offset(right - cornerLen, top), strokeWidth = cornerWidth)
            // Bottom-left
            drawLine(accentColor, Offset(left, bottom - 8), Offset(left, bottom - cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left + 8, bottom), Offset(left + cornerLen, bottom), strokeWidth = cornerWidth)
            // Bottom-right
            drawLine(accentColor, Offset(right, bottom - 8), Offset(right, bottom - cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right - 8, bottom), Offset(right - cornerLen, bottom), strokeWidth = cornerWidth)
        }

        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 48.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onBack?.invoke() },
                Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }

            Text("Scan QR / Barcode", color = Color.White, fontSize = 16.sp)

            Row {
                IconButton(
                    onClick = {
                        flashOn = !flashOn
                        cameraControl?.enableTorch(flashOn)
                    },
                    Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash", tint = Color.White)
                }
                IconButton(
                    onClick = { showHistory = true },
                    Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.History, "History", tint = Color.White)
                }
            }
        }

        // Bottom hint
        Text(
            "Point camera at a QR code or barcode",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ScannedResultContent(code: ScannedCode, context: Context, onDismiss: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(code.typeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(code.displayValue, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        // Actions based on type
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Copy
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Scanned code", code.rawValue))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, "Copy")
            }

            // Share
            IconButton(onClick = {
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, code.rawValue)
                    }, "Share"
                ))
            }) {
                Icon(Icons.Default.Share, "Share")
            }

            // URL: open in browser
            if (code.valueType == Barcode.TYPE_URL || code.rawValue.startsWith("http")) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.rawValue)))
                }) {
                    Icon(Icons.Default.OpenInBrowser, "Open URL")
                }
            }

            // WiFi: connect
            if (code.valueType == Barcode.TYPE_WIFI) {
                IconButton(onClick = {
                    Toast.makeText(context, "WiFi: ${code.displayValue}", Toast.LENGTH_LONG).show()
                }) {
                    Icon(Icons.Default.Wifi, "Connect WiFi")
                }
            }

            // Phone: dial
            if (code.valueType == Barcode.TYPE_PHONE) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${code.rawValue}")))
                }) {
                    Icon(Icons.Default.Link, "Call")
                }
            }

            // Email: compose
            if (code.valueType == Barcode.TYPE_EMAIL) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${code.rawValue}")))
                }) {
                    Icon(Icons.Default.Link, "Email")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onDismiss, Modifier.fillMaxWidth()) {
            Text("Scan Another")
        }

        Spacer(Modifier.height(24.dp))
    }
}

data class ScannedCode(
    val rawValue: String,
    val displayValue: String,
    val format: Int,
    val valueType: Int,
    val typeLabel: String
)

private fun getTypeLabel(valueType: Int): String {
    return when (valueType) {
        Barcode.TYPE_URL -> "URL"
        Barcode.TYPE_WIFI -> "WiFi"
        Barcode.TYPE_EMAIL -> "Email"
        Barcode.TYPE_PHONE -> "Phone"
        Barcode.TYPE_SMS -> "SMS"
        Barcode.TYPE_GEO -> "Location"
        Barcode.TYPE_CONTACT_INFO -> "Contact"
        Barcode.TYPE_CALENDAR_EVENT -> "Calendar Event"
        Barcode.TYPE_ISBN -> "ISBN"
        Barcode.TYPE_PRODUCT -> "Product"
        else -> "Code"
    }
}
