// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.qrscanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<QrHistoryItem>() }

    // Load history on first composition
    LaunchedEffect(Unit) {
        history.clear()
        history.addAll(QrHistoryRepository.load(context))
    }

    // Detail bottom sheet state
    var detailItem by remember { mutableStateOf<QrHistoryItem?>(null) }

    if (detailItem != null) {
        ModalBottomSheet(
            onDismissRequest = { detailItem = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            ScannedResultContent(
                item = detailItem!!,
                context = context,
                onDismiss = { detailItem = null }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Code") },
                navigationIcon = {
                    IconButton(onClick = { onBack?.invoke() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Scan") },
                    icon = { Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Generate") },
                    icon = { Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History") },
                    icon = { Icon(Icons.Default.History, null, Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> QrScanTab(
                    onBack = onBack,
                    onCodeScanned = { item ->
                        QrHistoryRepository.addItem(context, item)
                        history.add(0, item)
                        if (history.size > 200) history.removeLast()
                    },
                    onShowDetail = { detailItem = it }
                )
                1 -> QrGenerateTab(
                    onBack = onBack,
                    onCodeGenerated = { item ->
                        QrHistoryRepository.addItem(context, item)
                        history.add(0, item)
                        if (history.size > 200) history.removeLast()
                    }
                )
                2 -> QrHistoryTab(
                    onBack = onBack,
                    history = history,
                    onDelete = { id ->
                        QrHistoryRepository.deleteItem(context, id)
                        history.removeAll { it.id == id }
                    },
                    onClearAll = {
                        QrHistoryRepository.clearAll(context)
                        history.clear()
                    },
                    onItemClick = { detailItem = it }
                )
            }
        }
    }
}

// ─── Scan Tab ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanTab(
    onBack: (() -> Unit)?,
    onCodeScanned: (QrHistoryItem) -> Unit,
    onShowDetail: (QrHistoryItem) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }

    var scannedResult by remember { mutableStateOf<QrHistoryItem?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

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
                item = scannedResult!!,
                context = context,
                onDismiss = { showResult = false }
            )
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
                                            val item = QrHistoryItem(
                                                rawValue = barcode.rawValue ?: "",
                                                displayValue = barcode.displayValue ?: barcode.rawValue ?: "",
                                                format = barcode.format,
                                                valueType = barcode.valueType,
                                                typeLabel = getTypeLabel(barcode.valueType),
                                                source = QrSource.SCANNED
                                            )
                                            scannedResult = item
                                            showResult = true
                                            onCodeScanned(item)
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

        // Scan overlay
        Canvas(Modifier.fillMaxSize()) {
            val scanBoxSize = size.width * 0.7f
            val left = (size.width - scanBoxSize) / 2
            val top = (size.height - scanBoxSize) / 2
            val right = left + scanBoxSize
            val bottom = top + scanBoxSize
            val dimColor = Color.Black.copy(alpha = 0.5f)

            drawRect(dimColor, topLeft = Offset.Zero, size = Size(size.width, top))
            drawRect(dimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
            drawRect(dimColor, topLeft = Offset(0f, top), size = Size(left, scanBoxSize))
            drawRect(dimColor, topLeft = Offset(right, top), size = Size(size.width - right, scanBoxSize))

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(scanBoxSize, scanBoxSize),
                cornerRadius = CornerRadius(16f),
                style = Stroke(width = 3f)
            )

            val cornerLen = 30f
            val cornerWidth = 4f
            val accentColor = Color(0xFF4CAF50)
            drawLine(accentColor, Offset(left, top + 8), Offset(left, top + cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left + 8, top), Offset(left + cornerLen, top), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right, top + 8), Offset(right, top + cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right - 8, top), Offset(right - cornerLen, top), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left, bottom - 8), Offset(left, bottom - cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left + 8, bottom), Offset(left + cornerLen, bottom), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right, bottom - 8), Offset(right, bottom - cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right - 8, bottom), Offset(right - cornerLen, bottom), strokeWidth = cornerWidth)
        }

        // Flash toggle overlay
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = {
                    flashOn = !flashOn
                    cameraControl?.enableTorch(flashOn)
                },
                Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash", tint = Color.White)
            }
        }

        // Bottom hint
        Text(
            "Point camera at a QR code or barcode",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ─── Shared Result Content ──────────────────────────────────────────────────

@Composable
fun ScannedResultContent(item: QrHistoryItem, context: Context, onDismiss: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.typeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(
                if (item.source == QrSource.SCANNED) "Scanned" else "Generated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(item.displayValue, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Copy
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QR code", item.rawValue))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, "Copy")
            }

            // Share
            IconButton(onClick = {
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.rawValue)
                    }, "Share"
                ))
            }) {
                Icon(Icons.Default.Share, "Share")
            }

            // URL: open in browser
            if (item.valueType == Barcode.TYPE_URL || item.rawValue.startsWith("http")) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.rawValue)))
                }) {
                    Icon(Icons.Default.OpenInBrowser, "Open URL")
                }
            }

            // WiFi
            if (item.valueType == Barcode.TYPE_WIFI) {
                IconButton(onClick = {
                    Toast.makeText(context, "WiFi: ${item.displayValue}", Toast.LENGTH_LONG).show()
                }) {
                    Icon(Icons.Default.Wifi, "WiFi")
                }
            }

            // Phone: dial
            if (item.valueType == Barcode.TYPE_PHONE) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.rawValue}")))
                }) {
                    Icon(Icons.Default.Link, "Call")
                }
            }

            // Email: compose
            if (item.valueType == Barcode.TYPE_EMAIL) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${item.rawValue}")))
                }) {
                    Icon(Icons.Default.Link, "Email")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onDismiss, Modifier.fillMaxWidth()) {
            Text("Done")
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

fun getTypeLabel(valueType: Int): String {
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
