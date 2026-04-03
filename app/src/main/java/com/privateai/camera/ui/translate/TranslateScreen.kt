package com.privateai.camera.ui.translate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale

data class LangItem(val code: String, val name: String)

val LANGUAGES = listOf(
    LangItem(TranslateLanguage.ENGLISH, "English"),
    LangItem(TranslateLanguage.ARABIC, "Arabic"),
    LangItem(TranslateLanguage.FRENCH, "French"),
    LangItem(TranslateLanguage.SPANISH, "Spanish"),
    LangItem(TranslateLanguage.GERMAN, "German"),
    LangItem(TranslateLanguage.CHINESE, "Chinese"),
    LangItem(TranslateLanguage.JAPANESE, "Japanese"),
    LangItem(TranslateLanguage.KOREAN, "Korean"),
    LangItem(TranslateLanguage.PORTUGUESE, "Portuguese"),
    LangItem(TranslateLanguage.RUSSIAN, "Russian"),
    LangItem(TranslateLanguage.TURKISH, "Turkish"),
    LangItem(TranslateLanguage.ITALIAN, "Italian"),
    LangItem(TranslateLanguage.HINDI, "Hindi"),
    LangItem(TranslateLanguage.DUTCH, "Dutch"),
    LangItem(TranslateLanguage.POLISH, "Polish"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var sourceText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var sourceLang by remember { mutableStateOf(LANGUAGES[0]) }
    var targetLang by remember { mutableStateOf(LANGUAGES[2]) }
    var isTranslating by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var translatedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastImageFile by remember { mutableStateOf<File?>(null) }

    // TTS
    val ttsEngine = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) ttsEngine.value = engine
        }
        onDispose { engine?.shutdown() }
    }

    // Full-resolution camera capture
    val photoFile = remember { File(context.cacheDir, "translate_photo.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            lastImageFile = photoFile
            processImage(photoFile, scope, context, sourceLang, targetLang,
                onStart = { isProcessing = true; statusMessage = "Processing image..." },
                onOcrDone = { text -> sourceText = text },
                onTranslated = { text, bitmap ->
                    translatedText = text
                    translatedImageBitmap = bitmap
                    statusMessage = ""
                    isProcessing = false
                },
                onError = { msg -> statusMessage = msg; isProcessing = false }
            )
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                val tempFile = File(context.cacheDir, "translate_picked.jpg")
                tempFile.writeBytes(bytes)
                lastImageFile = tempFile
                processImage(tempFile, scope, context, sourceLang, targetLang,
                    onStart = { isProcessing = true; statusMessage = "Processing image..." },
                    onOcrDone = { text -> sourceText = text },
                    onTranslated = { text, bitmap ->
                        translatedText = text
                        translatedImageBitmap = bitmap
                        statusMessage = ""
                        isProcessing = false
                    },
                    onError = { msg -> statusMessage = msg; isProcessing = false }
                )
            }
        }
    }

    fun translateText() {
        if (sourceText.isBlank()) return

        // If we have an image, re-process it with new language
        val imgFile = lastImageFile
        if (imgFile != null && imgFile.exists()) {
            processImage(imgFile, scope, context, sourceLang, targetLang,
                onStart = { isTranslating = true; statusMessage = "Re-translating image..." },
                onOcrDone = { /* keep existing sourceText */ },
                onTranslated = { text, bitmap ->
                    translatedText = text
                    translatedImageBitmap?.recycle()
                    translatedImageBitmap = bitmap
                    statusMessage = ""
                    isTranslating = false
                },
                onError = { msg -> statusMessage = msg; isTranslating = false }
            )
            return
        }

        // Text-only translation
        isTranslating = true
        statusMessage = "Translating..."
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang.code)
            .setTargetLanguage(targetLang.code)
            .build()
        val translator = Translation.getClient(options)
        scope.launch {
            try {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                val result = translator.translate(sourceText).await()
                translatedText = result
                statusMessage = ""
            } catch (e: Exception) {
                statusMessage = "Translation failed: ${e.message}"
            } finally {
                isTranslating = false
                translator.close()
            }
        }
    }

    fun speakText(text: String, langCode: String) {
        val engine = ttsEngine.value ?: return
        engine.language = Locale.forLanguageTag(langCode)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts")
    }

    fun swapLanguages() {
        val tmp = sourceLang; sourceLang = targetLang; targetLang = tmp
        val tmpT = sourceText; sourceText = translatedText; translatedText = tmpT
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Translate") },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Performance warning for LOW tier devices
            if (com.privateai.camera.service.DeviceProfiler.getProfile(context).tier == com.privateai.camera.service.DeviceTier.LOW) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "⚡ Translation may be slow on this device",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Language selectors
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LanguageDropdown(sourceLang, LANGUAGES, { sourceLang = it }, Modifier.weight(1f))
                IconButton(onClick = { swapLanguages() }) { Icon(Icons.Default.SwapHoriz, "Swap") }
                LanguageDropdown(targetLang, LANGUAGES, { targetLang = it }, Modifier.weight(1f))
            }

            // Source text input
            OutlinedTextField(
                value = sourceText,
                onValueChange = { sourceText = it },
                label = { Text(sourceLang.name) },
                placeholder = { Text("Type, paste, or use camera") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                trailingIcon = {
                    if (sourceText.isNotBlank()) {
                        IconButton(onClick = { speakText(sourceText, sourceLang.code) }) {
                            Icon(Icons.Default.VolumeUp, "Listen")
                        }
                    }
                }
            )

            // Action buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cameraLauncher.launch(photoUri) }, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                    Text("  Camera")
                }
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                    Text("  Gallery")
                }
            }

            Button(
                onClick = { translateText() },
                enabled = sourceText.isNotBlank() && !isTranslating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTranslating || isProcessing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Translate, null, Modifier.size(18.dp))
                }
                Text("  Translate")
            }

            // Status
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, style = MaterialTheme.typography.bodySmall,
                    color = if (statusMessage.contains("failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Translated image with overlay (Google Lens style)
            translatedImageBitmap?.let { bmp ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Translated Image", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = {
                        translatedImageBitmap?.recycle()
                        translatedImageBitmap = null
                        lastImageFile = null
                    }) {
                        Icon(Icons.Default.Close, "Remove image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Translated image",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                )
            }

            // Translation text result
            if (translatedText.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(targetLang.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                IconButton(onClick = { speakText(translatedText, targetLang.code) }) { Icon(Icons.Default.VolumeUp, "Listen") }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(translatedText))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(translatedText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/**
 * Process an image: OCR → translate each text block → overlay translated text on image
 */
private fun processImage(
    imageFile: File,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    sourceLang: LangItem,
    targetLang: LangItem,
    onStart: () -> Unit,
    onOcrDone: (String) -> Unit,
    onTranslated: (String, Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    onStart()
    scope.launch {
        try {
            var originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: throw Exception("Failed to load image")

            // Fix EXIF rotation
            try {
                val exif = ExifInterface(imageFile.absolutePath)
                val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    val rotated = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                    originalBitmap.recycle()
                    originalBitmap = rotated
                }
            } catch (_: Exception) {}

            // OCR
            val image = InputImage.fromBitmap(originalBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val ocrResult = recognizer.process(image).await()

            if (ocrResult.text.isBlank()) {
                onError("No text found in image")
                originalBitmap.recycle()
                return@launch
            }

            onOcrDone(ocrResult.text)

            // Translate
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang.code)
                .setTargetLanguage(targetLang.code)
                .build()
            val translator = Translation.getClient(options)
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()

            // Translate each text block and overlay on image
            val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(resultBitmap)
            val fullTranslated = StringBuilder()

            for (block in ocrResult.textBlocks) {
                val translatedBlock = translator.translate(block.text).await()
                fullTranslated.appendLine(translatedBlock)

                val box = block.boundingBox ?: continue

                // Draw white background over original text
                val bgPaint = Paint().apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.FILL
                    alpha = 220
                }
                canvas.drawRect(box, bgPaint)

                // Draw translated text
                val textPaint = Paint().apply {
                    color = AndroidColor.rgb(0, 100, 200)
                    textSize = calculateTextSize(translatedBlock, box)
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                // Wrap text within the box
                drawTextInBox(canvas, translatedBlock, box, textPaint)
            }

            translator.close()
            onTranslated(fullTranslated.toString().trim(), resultBitmap)

        } catch (e: Exception) {
            onError("Failed: ${e.message}")
        }
    }
}

private fun calculateTextSize(text: String, box: Rect): Float {
    val boxHeight = box.height().toFloat()
    val boxWidth = box.width().toFloat()
    // Start with a size proportional to box height, then shrink if text is too wide
    var size = boxHeight * 0.6f
    val paint = Paint().apply { textSize = size }
    val lines = text.split("\n", " ").filter { it.isNotBlank() }
    val maxLineWidth = lines.maxOfOrNull { paint.measureText(it) } ?: boxWidth

    if (maxLineWidth > boxWidth) {
        size *= (boxWidth / maxLineWidth) * 0.9f
    }
    return size.coerceIn(12f, boxHeight * 0.8f)
}

private fun drawTextInBox(canvas: Canvas, text: String, box: Rect, paint: Paint) {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(testLine) <= box.width()) {
            currentLine = testLine
        } else {
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            currentLine = word
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine)

    val lineHeight = paint.textSize * 1.2f
    val totalHeight = lines.size * lineHeight
    var y = box.top + (box.height() - totalHeight) / 2 + paint.textSize

    for (line in lines) {
        val x = box.left + (box.width() - paint.measureText(line)) / 2
        canvas.drawText(line, x, y, paint)
        y += lineHeight
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selected: LangItem,
    languages: List<LangItem>,
    onSelect: (LangItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.name, onValueChange = {}, readOnly = true, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { lang ->
                DropdownMenuItem(text = { Text(lang.name) }, onClick = { onSelect(lang); expanded = false })
            }
        }
    }
}
