package com.privateai.camera.ui.scanner

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import java.io.File
import java.io.FileOutputStream

enum class EnhancementMode(val label: String) {
    ORIGINAL("Original"),
    AUTO("Auto"),
    BW("B&W"),
    COLOR("Color")
}

@Composable
fun ScannerScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var scannedPages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var enhancementMode by remember { mutableStateOf(EnhancementMode.ORIGINAL) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    var showOcrResult by remember { mutableStateOf(false) }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        scanResult?.pages?.let { pages ->
            scannedPages = pages.map { it.imageUri }
            currentPageIndex = 0
            ocrText = null
            showOcrResult = false
            if (pages.isNotEmpty()) {
                displayBitmap = loadAndEnhanceBitmap(context, pages[0].imageUri, enhancementMode)
            }
        }
    }

    fun startScan() {
        scanner.getStartScanIntent(context as android.app.Activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scanner error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun switchPage(index: Int) {
        if (index in scannedPages.indices) {
            currentPageIndex = index
            displayBitmap = loadAndEnhanceBitmap(context, scannedPages[index], enhancementMode)
            ocrText = null
            showOcrResult = false
        }
    }

    fun runOcr() {
        val bitmap = displayBitmap ?: return
        isProcessingOcr = true
        scope.launch {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = recognizer.process(image).await()
                ocrText = result.text
                showOcrResult = true
            } catch (e: Exception) {
                ocrText = "OCR failed: ${e.message}"
                showOcrResult = true
            } finally {
                isProcessingOcr = false
            }
        }
    }

    fun saveCurrentPageToGallery() {
        val bitmap = displayBitmap ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val filename = "scan_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrivateAICamera")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        uri?.let {
                            context.contentResolver.openOutputStream(it)?.use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                        }
                    } else {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val file = File(dir, filename)
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun saveAsPdfAndShare(shareAfterSave: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = PdfDocument()
                    // A4 at 150 DPI = 1240x1754 — good quality, reasonable file size
                    val maxWidth = 1240
                    val maxHeight = 1754

                    for (i in scannedPages.indices) {
                        val bmp = loadAndEnhanceBitmap(context, scannedPages[i], enhancementMode) ?: continue

                        // Scale down to fit within A4 at 150 DPI
                        val scale = minOf(maxWidth.toFloat() / bmp.width, maxHeight.toFloat() / bmp.height, 1f)
                        val scaledW = (bmp.width * scale).toInt()
                        val scaledH = (bmp.height * scale).toInt()
                        val scaled = if (scale < 1f) {
                            Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true).also { bmp.recycle() }
                        } else {
                            bmp
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(scaledW, scaledH, i + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        page.canvas.drawBitmap(scaled, 0f, 0f, null)
                        pdfDocument.finishPage(page)
                        if (i != currentPageIndex || scale < 1f) scaled.recycle()
                    }

                    val pdfFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(pdfFile).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()

                    withContext(Dispatchers.Main) {
                        if (shareAfterSave) {
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", pdfFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                        } else {
                            Toast.makeText(context, "PDF saved (${scannedPages.size} pages)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "PDF failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun shareCurrentPage() {
        val bitmap = displayBitmap ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(context.cacheDir, "share_scan.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share scan"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                }
            )
        }
    ) { scaffoldPadding ->

    if (scannedPages.isEmpty()) {
        // No scan yet
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.DocumentScanner,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("Document Scanner", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Scan documents, receipts, notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { startScan() }) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("  Scan Document", modifier = Modifier.padding(start = 4.dp))
            }
        }
    } else {
        // Scanned result
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Scanned image
            displayBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Scanned page",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            // Page navigation
            if (scannedPages.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { switchPage(currentPageIndex - 1) },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous page")
                    }
                    Text(
                        "Page ${currentPageIndex + 1} of ${scannedPages.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { switchPage(currentPageIndex + 1) },
                        enabled = currentPageIndex < scannedPages.size - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next page")
                    }
                }
            }

            // Enhancement chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancementMode.entries.forEach { mode ->
                    FilterChip(
                        selected = enhancementMode == mode,
                        onClick = {
                            enhancementMode = mode
                            displayBitmap = loadAndEnhanceBitmap(
                                context, scannedPages[currentPageIndex], mode
                            )
                        },
                        label = { Text(mode.label) }
                    )
                }
            }

            // Action buttons row 1: Save & Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { saveCurrentPageToGallery() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Save Image")
                }
                OutlinedButton(onClick = { shareCurrentPage() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Share")
                }
            }

            // Action buttons row 2: PDF & OCR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { saveAsPdfAndShare(shareAfterSave = true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Share PDF")
                }
                OutlinedButton(
                    onClick = { runOcr() },
                    enabled = !isProcessingOcr,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isProcessingOcr) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text("  Extract Text")
                }
            }

            // New scan button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedButton(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  New Scan")
                }
            }

            // OCR result
            if (showOcrResult && ocrText != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Extracted Text", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(ocrText!!))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = ocrText!!.ifEmpty { "(No text found)" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    } // Scaffold
}

private fun loadAndEnhanceBitmap(
    context: android.content.Context,
    uri: Uri,
    mode: EnhancementMode
): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        when (mode) {
            EnhancementMode.ORIGINAL -> original
            EnhancementMode.AUTO -> applyAutoEnhance(original)
            EnhancementMode.BW -> applyBlackWhite(original)
            EnhancementMode.COLOR -> applyColorEnhance(original)
        }
    } catch (e: Exception) {
        null
    }
}

private fun applyAutoEnhance(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix(floatArrayOf(
        1.3f, 0f, 0f, 0f, -30f,
        0f, 1.3f, 0f, 0f, -30f,
        0f, 0f, 1.3f, 0f, -30f,
        0f, 0f, 0f, 1f, 0f
    ))
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

private fun applyBlackWhite(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix().apply { setSaturation(0f) }
    val contrastCm = ColorMatrix(floatArrayOf(
        2.0f, 0f, 0f, 0f, -180f,
        0f, 2.0f, 0f, 0f, -180f,
        0f, 0f, 2.0f, 0f, -180f,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrastCm)
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

private fun applyColorEnhance(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix().apply { setSaturation(1.5f) }
    val contrastCm = ColorMatrix(floatArrayOf(
        1.15f, 0f, 0f, 0f, -15f,
        0f, 1.15f, 0f, 0f, -15f,
        0f, 0f, 1.15f, 0f, -15f,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrastCm)
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}
