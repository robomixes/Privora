package com.privateai.camera.ui.vault

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

// Screens: LOCKED -> CATEGORIES -> GALLERY -> VIEWER / VIDEO_PLAYER
private enum class VaultPage { LOCKED, CATEGORIES, GALLERY, VIEWER, VIDEO_PLAYER }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val crypto = remember { CryptoManager(context) }
    val vault = remember { VaultRepository(context, crypto) }

    // Check if already unlocked within grace period (e.g. from Notes, or returning quickly)
    val startUnlocked = remember {
        VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize()
    }
    var page by remember { mutableStateOf(if (startUnlocked) VaultPage.CATEGORIES else VaultPage.LOCKED) }
    var currentCategory by remember { mutableStateOf(VaultCategory.CAMERA) }
    var photos by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var categoryCounts by remember { mutableStateOf<Map<VaultCategory, Int>>(emptyMap()) }

    // Viewer state
    var viewerPhoto by remember { mutableStateOf<VaultPhoto?>(null) }
    var viewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoTempFile by remember { mutableStateOf<File?>(null) }

    // Duress mode — blocks all data access when active
    var isDuressActive by remember { mutableStateOf(false) }

    // Selection
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Auto-lock with shared grace period
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    VaultLockManager.markLeft()
                }
                Lifecycle.Event.ON_START -> {
                    if (page != VaultPage.LOCKED && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        page = VaultPage.LOCKED
                        crypto.lock()
                        VaultLockManager.lock()
                        thumbnails.values.forEach { it.recycle() }
                        thumbnails = emptyMap()
                        viewerBitmap?.recycle()
                        viewerBitmap = null
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            thumbnails.values.forEach { it.recycle() }
            viewerBitmap?.recycle()
            videoTempFile?.delete()
        }
    }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            if (crypto.initialize()) {
                VaultLockManager.markUnlocked()
                categoryCounts = vault.countByCategory()
                page = VaultPage.CATEGORIES
            }
            return
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) {
                        VaultLockManager.markUnlocked()
                        categoryCounts = vault.countByCategory()
                        page = VaultPage.CATEGORIES
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Authenticate to access encrypted photos")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ).build()
        )
    }

    fun openCategory(cat: VaultCategory) {
        if (isDuressActive) {
            // Duress mode: show empty gallery
            photos = emptyList()
            thumbnails = emptyMap()
            currentCategory = cat
            page = VaultPage.GALLERY
            return
        }
        scope.launch {
            if (!crypto.isUnlocked()) crypto.initialize()
            val loaded = withContext(Dispatchers.IO) { vault.listPhotos(cat) }
            val thumbMap = mutableMapOf<String, Bitmap>()
            withContext(Dispatchers.IO) {
                loaded.forEach { photo ->
                    vault.loadThumbnail(photo)?.let { thumbMap[photo.id] = it }
                }
            }
            photos = loaded
            thumbnails = thumbMap
            currentCategory = cat
            selectedIds = emptySet()
            isSelectionMode = false
            page = VaultPage.GALLERY
        }
    }

    fun openViewer(photo: VaultPhoto) {
        if (photo.mediaType == VaultMediaType.VIDEO) {
            scope.launch {
                val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(photo) }
                if (tempFile != null) {
                    videoTempFile = tempFile
                    viewerPhoto = photo
                    page = VaultPage.VIDEO_PLAYER
                } else {
                    Toast.makeText(context, "Failed to decrypt video", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(photo) }
                viewerPhoto = photo
                viewerBitmap = bmp
                page = VaultPage.VIEWER
            }
        }
    }

    fun deletePhotos(ids: Set<String>) {
        val toDelete = photos.filter { it.id in ids }
        toDelete.forEach { vault.deletePhoto(it) }
        toDelete.forEach { thumbnails[it.id]?.recycle() }
        thumbnails = thumbnails - ids
        photos = photos.filter { it.id !in ids }
        categoryCounts = vault.countByCategory()
        selectedIds = emptySet()
        isSelectionMode = false
    }

    fun sharePdf(ids: Set<String>) {
        val toInclude = photos.filter { it.id in ids }
        scope.launch {
            withContext(Dispatchers.IO) {
                val pdf = PdfDocument()
                toInclude.forEachIndexed { i, photo ->
                    val bmp = vault.loadFullPhoto(photo) ?: return@forEachIndexed
                    val scale = minOf(1240f / bmp.width, 1754f / bmp.height, 1f)
                    val w = (bmp.width * scale).toInt()
                    val h = (bmp.height * scale).toInt()
                    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() } else bmp
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                    page.canvas.drawBitmap(scaled, 0f, 0f, null)
                    pdf.finishPage(page)
                    scaled.recycle()
                }
                val pdfFile = File(context.cacheDir, "vault_${System.currentTimeMillis()}.pdf")
                FileOutputStream(pdfFile).use { pdf.writeTo(it) }
                pdf.close()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share PDF"
                    ))
                    selectedIds = emptySet()
                    isSelectionMode = false
                }
            }
        }
    }

    fun sharePhoto(photo: VaultPhoto) {
        scope.launch {
            withContext(Dispatchers.IO) {
                var bitmap = vault.loadFullPhoto(photo) ?: return@withContext

                // Face blur if enabled
                if (com.privateai.camera.ui.settings.isFaceBlurEnabled(context)) {
                    bitmap = com.privateai.camera.util.FaceBlur.blurFaces(bitmap)
                }

                val uri = com.privateai.camera.util.saveBitmapToCache(context, bitmap, "vault_share.jpg")
                bitmap.recycle()
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share photo"
                    ))
                }
            }
        }
    }

    fun shareImages(ids: Set<String>) {
        val toShare = photos.filter { it.id in ids }
        val hasVideos = toShare.any { it.mediaType == VaultMediaType.VIDEO }
        scope.launch {
            withContext(Dispatchers.IO) {
                val uris = ArrayList<android.net.Uri>()
                toShare.forEach { photo ->
                    if (photo.mediaType == VaultMediaType.VIDEO) {
                        val tempFile = vault.decryptVideoToTempFile(photo) ?: return@forEach
                        uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile))
                    } else {
                        val bytes = vault.loadPhotoBytes(photo) ?: return@forEach
                        uris.add(com.privateai.camera.util.saveJpegBytesToCache(context, bytes, "vault_share_${photo.id}.jpg"))
                    }
                }
                val mimeType = if (hasVideos && toShare.any { it.mediaType == VaultMediaType.PHOTO }) "*/*" else if (hasVideos) "video/mp4" else "image/jpeg"
                withContext(Dispatchers.Main) {
                    if (uris.size == 1) {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uris[0])
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share"
                        ))
                    } else {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = mimeType
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share ${uris.size} items"
                        ))
                    }
                    selectedIds = emptySet()
                    isSelectionMode = false
                }
            }
        }
    }

    fun saveToDevice(ids: Set<String>) {
        val toSave = photos.filter { it.id in ids }
        scope.launch {
            var saved = 0
            withContext(Dispatchers.IO) {
                toSave.forEach { photo ->
                    try {
                        if (photo.mediaType == VaultMediaType.VIDEO) {
                            val bytes = crypto.decryptFile(photo.encryptedFile)
                            val filename = "vault_${photo.id}.mp4"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PrivateAICamera")
                                }
                                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                            }
                        } else {
                            val bytes = vault.loadPhotoBytes(photo) ?: return@forEach
                            val filename = "vault_${photo.id}.jpg"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrivateAICamera")
                                }
                                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                            } else {
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                FileOutputStream(File(dir, filename)).use { it.write(bytes) }
                            }
                        }
                        saved++
                    } catch (_: Exception) {}
                }
            }
            Toast.makeText(context, "$saved item(s) saved to gallery", Toast.LENGTH_SHORT).show()
            selectedIds = emptySet()
            isSelectionMode = false
        }
    }

    // Delete dialog
    if (showDeleteDialog) {
        val count = if (isSelectionMode) selectedIds.size else 1
        val isVideo = viewerPhoto?.mediaType == VaultMediaType.VIDEO
        val itemLabel = when {
            isSelectionMode && count > 1 -> "$count Items"
            isVideo -> "Video"
            else -> "Photo"
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete $itemLabel") },
            text = { Text("Permanently deleted from vault.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    if (isSelectionMode) deletePhotos(selectedIds)
                    else viewerPhoto?.let {
                        deletePhotos(setOf(it.id))
                        videoTempFile?.delete()
                        videoTempFile = null
                        page = VaultPage.GALLERY
                    }
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    // PIN input state for lock screen
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun checkPin(enteredPin: String) {
        // Check duress PIN first
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, enteredPin)) {
            // DURESS: show empty vault, block all data loading
            isDuressActive = true
            VaultLockManager.markUnlocked()
            categoryCounts = VaultCategory.entries.associateWith { 0 }
            photos = emptyList()
            thumbnails = emptyMap()
            page = VaultPage.CATEGORIES
            pinInput = ""
            pinError = null

            // Background wipe (if mode == WIPE)
            scope.launch(Dispatchers.IO) {
                DuressManager.executeDuress(context, crypto)
            }
            return
        }

        // Check app PIN
        val appPin = com.privateai.camera.ui.onboarding.getAppPin(context)
        if (appPin != null && enteredPin == appPin) {
            if (crypto.initialize()) {
                VaultLockManager.markUnlocked()
                categoryCounts = vault.countByCategory()
                page = VaultPage.CATEGORIES
                pinInput = ""
                pinError = null
            }
            return
        }

        // Wrong PIN
        pinError = "Incorrect PIN"
        pinInput = ""
    }

    val currentAuthMode = remember { getAuthMode(context) }

    // Auto-authenticate if phone lock mode
    LaunchedEffect(Unit) {
        if (page == VaultPage.LOCKED) {
            if (currentAuthMode == AuthMode.PHONE_LOCK) {
                authenticate() // biometric/device credential only
            }
        } else if (!isDuressActive) {
            categoryCounts = vault.countByCategory()
        }
    }

    when (page) {
        VaultPage.LOCKED -> {
            Scaffold(topBar = {
                TopAppBar(title = { Text("Encrypted Vault") }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                })
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Vault is locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

                    Spacer(Modifier.height(24.dp))

                    if (currentAuthMode == AuthMode.APP_PIN) {
                        // App PIN mode: PIN field + optional biometric
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                                    pinInput = it
                                    pinError = null
                                }
                            },
                            label = { Text("Enter PIN") },
                            modifier = Modifier.width(200.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pinError != null,
                            supportingText = {
                                if (pinError != null) Text(pinError!!, color = MaterialTheme.colorScheme.error)
                            }
                        )

                        Button(
                            onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                            enabled = pinInput.length >= 4,
                            modifier = Modifier.width(200.dp)
                        ) { Text("Unlock") }

                        Spacer(Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { authenticate() },
                            modifier = Modifier.width(200.dp)
                        ) { Text("Use Biometric") }
                    } else {
                        // Phone lock mode: biometric/device credential only
                        Button(
                            onClick = { authenticate() },
                            modifier = Modifier.width(200.dp)
                        ) { Text("Unlock") }
                    }
                }
            }
        }

        VaultPage.CATEGORIES -> {
            Scaffold(topBar = {
                TopAppBar(title = { Text("Encrypted Vault") }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                })
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryCard("Camera Photos", categoryCounts[VaultCategory.CAMERA] ?: 0, Icons.Default.CameraAlt) { openCategory(VaultCategory.CAMERA) }
                    CategoryCard("Videos", categoryCounts[VaultCategory.VIDEO] ?: 0, Icons.Default.Videocam) { openCategory(VaultCategory.VIDEO) }
                    CategoryCard("Scanned Documents", categoryCounts[VaultCategory.SCAN] ?: 0, Icons.Default.DocumentScanner) { openCategory(VaultCategory.SCAN) }
                }
            }
        }

        VaultPage.GALLERY -> {
            Scaffold(topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} selected") },
                        navigationIcon = { IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, "Cancel") } },
                        actions = {
                            IconButton(onClick = { shareImages(selectedIds) }) { Icon(Icons.Default.Share, "Share Images") }
                            IconButton(onClick = { sharePdf(selectedIds) }) { Icon(Icons.Default.PictureAsPdf, "Share PDF") }
                            IconButton(onClick = { saveToDevice(selectedIds) }) { Icon(Icons.Default.SaveAlt, "Save to Device") }
                            IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text("${currentCategory.label} (${photos.size})") },
                        navigationIcon = {
                            IconButton(onClick = {
                                thumbnails.values.forEach { it.recycle() }
                                thumbnails = emptyMap()
                                if (!isDuressActive) categoryCounts = vault.countByCategory()
                                page = VaultPage.CATEGORIES
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        }
                    )
                }
            }) { padding ->
                if (photos.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("No items yet", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val grouped = remember(photos) { groupPhotosByDate(photos) }

                    LazyColumn(
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(padding)
                    ) {
                        grouped.forEach { (header, groupPhotos) ->
                            item {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            item {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    groupPhotos.forEach { photo ->
                                        val thumb = thumbnails[photo.id]
                                        val isSelected = photo.id in selectedIds
                                        val itemSize = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 24.dp) / 3

                                        Box(
                                            Modifier
                                                .size(itemSize)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                                        } else openViewer(photo)
                                                    },
                                                    onLongClick = { isSelectionMode = true; selectedIds = setOf(photo.id) }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumb != null) {
                                                Image(thumb.asImageBitmap(), "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            // Video play icon overlay
                                            if (photo.mediaType == VaultMediaType.VIDEO) {
                                                Icon(
                                                    Icons.Default.PlayCircleFilled,
                                                    contentDescription = "Video",
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(32.dp).align(Alignment.Center)
                                                )
                                            }
                                            if (isSelectionMode && isSelected) {
                                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        VaultPage.VIEWER -> {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                viewerBitmap?.let { bmp ->
                    Image(bmp.asImageBitmap(), "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                IconButton(
                    onClick = { viewerBitmap?.recycle(); viewerBitmap = null; page = VaultPage.GALLERY },
                    Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 16.dp).size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }

                val blurDefault = com.privateai.camera.ui.settings.isFaceBlurEnabled(context)

                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share (respects global setting)
                    IconButton(onClick = { viewerPhoto?.let { sharePhoto(it) } }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                    // Toggle button: opposite of global setting
                    IconButton(onClick = {
                        viewerPhoto?.let { photo ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    var bitmap = vault.loadFullPhoto(photo) ?: return@withContext
                                    if (!blurDefault) {
                                        // Setting OFF → this button blurs
                                        bitmap = com.privateai.camera.util.FaceBlur.blurFaces(bitmap)
                                    }
                                    // Setting ON → this button shares clean (no blur)
                                    val uri = com.privateai.camera.util.saveBitmapToCache(context, bitmap, "vault_alt_share.jpg")
                                    bitmap.recycle()
                                    withContext(Dispatchers.Main) {
                                        val label = if (blurDefault) "Share (no blur)" else "Share (faces blurred)"
                                        context.startActivity(Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "image/jpeg"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }, label
                                        ))
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.Face,
                            if (blurDefault) "Share without blur" else "Blur & Share",
                            tint = if (blurDefault) Color.White else Color(0xFF4CAF50)
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF6B6B))
                    }
                }
            }
        }

        VaultPage.VIDEO_PLAYER -> {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                videoTempFile?.let { file ->
                    com.privateai.camera.ui.camera.VideoPlayerWithControls(
                        videoFile = file,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Back button
                IconButton(
                    onClick = {
                        videoTempFile?.delete()
                        videoTempFile = null
                        page = VaultPage.GALLERY
                    },
                    Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 16.dp)
                        .size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }

                // Bottom bar: share + delete
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share video
                    IconButton(onClick = {
                        viewerPhoto?.let { photo ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val tempFile = vault.decryptVideoToTempFile(photo) ?: return@withContext
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }, "Share Video"
                                        ))
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                    // Save to device
                    IconButton(onClick = {
                        viewerPhoto?.let { photo ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val bytes = vault.loadFile(photo.encryptedFile) ?: return@withContext
                                        val filename = "vault_${photo.id}.mp4"
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            val values = ContentValues().apply {
                                                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PrivateAICamera")
                                            }
                                            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                            uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Video saved to gallery", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.SaveAlt, "Save to Device", tint = Color.White)
                    }
                    // Delete
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF6B6B))
                    }
                }
            }
        }
    }
}

private fun groupPhotosByDate(photos: List<VaultPhoto>): List<Pair<String, List<VaultPhoto>>> {
    val now = Calendar.getInstance()
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekAgo = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    val monthAgo = (today.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val groups = linkedMapOf<String, MutableList<VaultPhoto>>()

    for (photo in photos) {
        val photoTime = Calendar.getInstance().apply { timeInMillis = photo.timestamp }
        val label = when {
            photoTime >= today -> "Today"
            photoTime >= yesterday -> "Yesterday"
            photoTime >= weekAgo -> dayFormat.format(Date(photo.timestamp))
            photoTime >= monthAgo -> "This Month"
            else -> monthYearFormat.format(Date(photo.timestamp))
        }
        groups.getOrPut(label) { mutableListOf() }.add(photo)
    }

    return groups.map { (k, v) -> k to v.toList() }
}

@Composable
private fun CategoryCard(
    label: String, count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, label, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text("$count items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
