package com.privateai.camera.ui.vault

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.FolderManager
import com.privateai.camera.security.VaultFolder
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
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.text.style.TextAlign
import com.privateai.camera.bridge.FaceEmbedder
import com.privateai.camera.bridge.ImageClassifier
import com.privateai.camera.security.PhotoIndex
import com.privateai.camera.security.PrivoraDatabase

// Screens: LOCKED -> CATEGORIES -> GALLERY -> VIEWER / VIDEO_PLAYER
private enum class VaultPage { LOCKED, CATEGORIES, GALLERY, VIEWER, VIDEO_PLAYER, FOLDER_VIEW, TRASH }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(onBack: (() -> Unit)? = null, initialSearchQuery: String = "") {
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
    var trashCount by remember { mutableIntStateOf(0) }
    var trashItems by remember { mutableStateOf<List<VaultRepository.TrashedItem>>(emptyList()) }
    var trashThumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Viewer state
    var viewerPhoto by remember { mutableStateOf<VaultPhoto?>(null) }
    var viewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoTempFile by remember { mutableStateOf<File?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    // Custom folders
    val folderManager = remember { FolderManager(context, crypto) }
    var rootFolders by remember { mutableStateOf<List<VaultFolder>>(emptyList()) }
    var currentFolder by remember { mutableStateOf<VaultFolder?>(null) }
    var subfolders by remember { mutableStateOf<List<VaultFolder>>(emptyList()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var editorPhoto by remember { mutableStateOf<VaultPhoto?>(null) }
    var editorBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Duress mode — blocks all data access when active
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }

    // Search
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var searchResults by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchThumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Selection
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Photo Intelligence
    var photoIndex by remember { mutableStateOf<PhotoIndex?>(null) }
    var classifier by remember { mutableStateOf<ImageClassifier?>(null) }
    var isIndexing by remember { mutableStateOf(false) }
    var indexProgress by remember { mutableStateOf(0 to 0) }  // (done, total)
    var smartMode by remember { mutableStateOf<String?>(null) } // "duplicates", "blurry", or null
    var showFaceGroups by remember { mutableStateOf(false) }
    var faceGroups by remember { mutableStateOf<Map<String, List<Triple<String, Int, PhotoIndex.FaceEntry>>>>(emptyMap()) }
    var selectedFaceGroup by remember { mutableStateOf<String?>(null) }
    var renamingGroup by remember { mutableStateOf<String?>(null) }
    var mergeSourceGroup by remember { mutableStateOf<String?>(null) } // first group selected for merge
    var mergeTargetGroup by remember { mutableStateOf<String?>(null) } // second group to confirm merge
    var searchFromViewer by remember { mutableStateOf(false) } // true = back returns to VIEWER
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var duplicateGroups by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    // Virtual smart views
    var isVirtualPhotos by remember { mutableStateOf(false) }
    var isVirtualVideos by remember { mutableStateOf(false) }

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
                        // thumbnails cleared — GC handles bitmap recycling (Compose may still be drawing)
                        thumbnails = emptyMap()
                        // viewerBitmap cleared — GC handles recycling
                        viewerBitmap = null
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // thumbnails cleared — GC handles bitmap recycling (Compose may still be drawing)
            // viewerBitmap cleared — GC handles recycling
            videoTempFile?.delete()
        }
    }

    // Import files launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var importedImages = 0
            var importedPdfs = 0
            var importedVideos = 0
            var skippedLarge = 0
            // Keep vault unlocked during import
            VaultLockManager.markUnlocked()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    VaultLockManager.markUnlocked() // Keep alive during long imports
                    try {
                        val mimeType = context.contentResolver.getType(uri)

                        // Import to custom folder if in FOLDER_VIEW, else system categories
                        val folderDir = if (page == VaultPage.FOLDER_VIEW) {
                            currentFolder?.let { folderManager.getFolderDir(it.id) }
                        } else null

                        if (mimeType?.startsWith("video/") == true) {
                            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                            if (size > 150L * 1024 * 1024) { skippedLarge++; return@forEach }
                            val tempFile = java.io.File(context.cacheDir, "import_vid_${System.currentTimeMillis()}.mp4")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            if (folderDir != null) {
                                // Save video to custom folder
                                vault.saveVideo(tempFile, VaultCategory.VIDEO)
                                // Move the just-saved video to folder
                                val videoItems = vault.listPhotos(VaultCategory.VIDEO)
                                videoItems.firstOrNull()?.let { vault.moveToFolder(it, folderDir) }
                            } else {
                                vault.saveVideo(tempFile)
                            }
                            importedVideos++
                        } else {
                            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@forEach
                            if (mimeType?.startsWith("image/") == true) {
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEach
                                if (folderDir != null) {
                                    vault.savePhotoToFolder(bitmap, folderDir)
                                } else {
                                    vault.savePhoto(bitmap, VaultCategory.CAMERA)
                                }
                                bitmap.recycle()
                                importedImages++
                            } else if (mimeType == "application/pdf") {
                                if (folderDir != null) {
                                    vault.saveFile(bytes, "import_${System.currentTimeMillis()}.pdf", VaultCategory.FILES)
                                    // Move PDF to folder
                                    val pdfFile = File(folderDir, "import_${System.currentTimeMillis()}.pdf.enc")
                                    crypto.encryptToFile(bytes, pdfFile)
                                } else {
                                    vault.saveFile(bytes, "import_${System.currentTimeMillis()}.pdf", VaultCategory.SCAN)
                                }
                                importedPdfs++
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
            // Refresh folder view if importing into a folder
            val refreshFolder = currentFolder
            if (page == VaultPage.FOLDER_VIEW && refreshFolder != null) {
                val dir = folderManager.getFolderDir(refreshFolder.id)
                photos = vault.listFolderItems(dir)
                // Load thumbnails for newly imported items
                withContext(Dispatchers.IO) {
                    val thumbMap = thumbnails.toMutableMap()
                    photos.forEach { photo ->
                        if (photo.id !in thumbMap) {
                            vault.loadThumbnail(photo)?.let { thumbMap[photo.id] = it }
                        }
                    }
                    withContext(Dispatchers.Main) { thumbnails = thumbMap }
                }
            }
            val parts = mutableListOf<String>()
            if (importedImages > 0) parts.add(context.getString(R.string.n_photos, importedImages))
            if (importedVideos > 0) parts.add(context.getString(R.string.n_videos, importedVideos))
            if (importedPdfs > 0) parts.add(context.getString(R.string.n_pdfs, importedPdfs))
            val dest = if (currentFolder != null && page == VaultPage.FOLDER_VIEW) context.getString(R.string.to_folder, currentFolder?.name.orEmpty()) else ""
            val msg = if (parts.isNotEmpty()) context.getString(R.string.imported_summary, parts.joinToString(" + "), dest) else ""
            val skipMsg = if (skippedLarge > 0) context.getString(R.string.skipped_large, skippedLarge) else ""
            if (msg.isNotEmpty() || skipMsg.isNotEmpty()) {
                Toast.makeText(context, "$msg$skipMsg", Toast.LENGTH_LONG).show()
            }
            // Auto-index newly imported photos in background
            val pi = photoIndex
            val cl = classifier
            if (pi != null && cl != null && importedImages > 0) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val fe = try { com.privateai.camera.bridge.FaceEmbedder(context) } catch (_: Exception) { null }
                        val fromCats = vault.listAllPhotos()
                        val fromFolders = folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
                        val allPhotos = (fromCats + fromFolders).distinctBy { p -> p.id }.filter { p -> p.mediaType == VaultMediaType.PHOTO }
                        val unindexed = allPhotos.filter { p -> !pi.isIndexed(p.id) }
                        if (unindexed.isNotEmpty()) {
                            isIndexing = true
                            unindexed.forEachIndexed { i, photo ->
                                val bmp = vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                                bmp?.let { img ->
                                    try { pi.indexPhoto(photo.id, img, cl, faceEmbedder = fe) } catch (_: Exception) {}
                                    img.recycle()
                                }
                                indexProgress = i + 1 to unindexed.size
                            }
                            isIndexing = false
                        }
                        fe?.release()
                    }
                }
            }
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
                categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                page = VaultPage.CATEGORIES
            }
            return
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) {
                        VaultLockManager.markUnlocked()
                        categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                        page = VaultPage.CATEGORIES
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.unlock_vault))
                .setSubtitle(context.getString(R.string.authenticate_to_access_vault))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ).build()
        )
    }

    /** Get ALL items from all categories + all folders (unified view for AI). */
    fun getAllVaultItems(): List<VaultPhoto> {
        val fromCategories = vault.listAllPhotos()
        val fromFolders = folderManager.listAllFolders().flatMap { vault.listFolderItems(folderManager.getFolderDir(it.id)) }
        return (fromCategories + fromFolders).distinctBy { it.id }.sortedByDescending { it.timestamp }
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
        when (photo.mediaType) {
            VaultMediaType.VIDEO -> {
                scope.launch {
                    val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(photo) }
                    if (tempFile != null) {
                        videoTempFile = tempFile
                        viewerPhoto = photo
                        page = VaultPage.VIDEO_PLAYER
                    } else {
                        Toast.makeText(context, context.getString(R.string.failed_to_decrypt_video), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            VaultMediaType.PDF -> {
                // Decrypt PDF to temp and open/share
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val decrypted = crypto.decryptFile(photo.encryptedFile)
                            val tempPdf = File(context.cacheDir, "${photo.id}.pdf")
                            tempPdf.writeBytes(decrypted)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempPdf)
                            withContext(Dispatchers.Main) {
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }, context.getString(R.string.open_pdf)
                                ))
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.failed_to_open_pdf, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            else -> {
                scope.launch {
                    val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(photo) }
                    viewerPhoto = photo
                    viewerBitmap = bmp
                    page = VaultPage.VIEWER
                }
            }
        }
    }

    fun deletePhotos(ids: Set<String>) {
        // Move to trash instead of permanent delete — keep index entries for fast restore
        val allKnown = (photos + searchResults).distinctBy { it.id }
        val toDelete = allKnown.filter { it.id in ids }
        toDelete.forEach { vault.moveToTrash(it) }
        thumbnails = thumbnails - ids
        photos = photos.filter { it.id !in ids }
        searchResults = searchResults.filter { it.id !in ids }
        searchThumbnails = searchThumbnails - ids
        // Don't remove from photoIndex — keep entries so restore doesn't need re-indexing
        categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount()
        selectedIds = emptySet()
        isSelectionMode = false
        Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
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
                        }, context.getString(R.string.share_pdf)
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
                        }, context.getString(R.string.share_photo)
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
                            }, context.getString(R.string.share)
                        ))
                    } else {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = mimeType
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, context.getString(R.string.share_n_items, uris.size)
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
            Toast.makeText(context, context.getString(R.string.items_saved_to_gallery, saved), Toast.LENGTH_SHORT).show()
            selectedIds = emptySet()
            isSelectionMode = false
        }
    }

    // Details dialog
    var showDetailsDialog by remember { mutableStateOf(false) }
    val detailsItem = viewerPhoto
    if (showDetailsDialog && detailsItem != null) {
        val item = detailsItem
        val encSize = if (item.encryptedFile.exists()) item.encryptedFile.length() else 0L
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(item.timestamp))
        val typeLabel = when (item.mediaType) {
            VaultMediaType.PHOTO -> context.getString(R.string.type_photo_jpeg)
            VaultMediaType.VIDEO -> context.getString(R.string.type_video_mp4)
            VaultMediaType.PDF -> context.getString(R.string.type_pdf_document)
        }

        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(stringResource(R.string.file_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(stringResource(R.string.detail_type), typeLabel)
                    DetailRow(stringResource(R.string.detail_name), item.id)
                    DetailRow(stringResource(R.string.detail_date), dateStr)
                    DetailRow(stringResource(R.string.detail_encrypted_size), com.privateai.camera.service.StorageManager.formatSize(encSize))
                    DetailRow(stringResource(R.string.detail_category), item.category.label)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.security), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    DetailRow(stringResource(R.string.detail_encryption), stringResource(R.string.aes_256_gcm))
                    DetailRow(stringResource(R.string.detail_key_storage), stringResource(R.string.hardware_tee_strongbox))
                    DetailRow(stringResource(R.string.detail_exif_data), stringResource(R.string.stripped_on_share))
                    DetailRow(stringResource(R.string.detail_storage), stringResource(R.string.app_internal_hidden))
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.create_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName, onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.folder_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        folderManager.createFolder(folderName.trim(), currentFolder?.id)
                        rootFolders = folderManager.listRootFolders()
                        currentFolder?.let { subfolders = folderManager.listSubfolders(it.id) }
                    }
                    showCreateFolderDialog = false
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Rename folder dialog
    val renameFolder = currentFolder
    if (showRenameFolderDialog && renameFolder != null) {
        var newName by remember { mutableStateOf(renameFolder.name) }
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = { Text(stringResource(R.string.rename_folder)) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.new_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        folderManager.renameFolder(renameFolder.id, newName.trim())
                        currentFolder = renameFolder.copy(name = newName.trim())
                        rootFolders = folderManager.listRootFolders()
                    }
                    showRenameFolderDialog = false
                }) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = { TextButton(onClick = { showRenameFolderDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Delete folder dialog
    val deleteFolder = currentFolder
    if (showDeleteFolderDialog && deleteFolder != null) {
        AlertDialog(
            onDismissRequest = { showDeleteFolderDialog = false },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = { Text(stringResource(R.string.delete_folder_message, deleteFolder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    folderManager.deleteFolder(deleteFolder.id)
                    rootFolders = folderManager.listRootFolders()
                    showDeleteFolderDialog = false
                    currentFolder = null
                    page = VaultPage.CATEGORIES
                }) { Text(stringResource(R.string.delete), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteFolderDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Move to folder dialog
    if (showMoveDialog) {
        val allFolders = remember { folderManager.listAllFolders() }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text(stringResource(R.string.move_to)) },
            text = {
                Column(
                    Modifier.height(300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // System categories
                    Text(stringResource(R.string.system_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    VaultCategory.entries.filter { it != VaultCategory.FILES }.forEach { cat ->
                        TextButton(onClick = {
                            val toMove = photos.filter { it.id in selectedIds }
                            toMove.forEach { photo ->
                                val targetDir = File(context.filesDir, "vault/${cat.dirName}")
                                vault.moveToFolder(photo, targetDir)
                            }
                            photos = photos.filter { it.id !in selectedIds }
                            selectedIds = emptySet(); isSelectionMode = false
                            if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
                            showMoveDialog = false
                            Toast.makeText(context, context.getString(R.string.moved_n_items, toMove.size), Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(cat.label, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    if (allFolders.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.my_folders), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        allFolders.forEach { folder ->
                            val path = folderManager.getFolderPath(folder.id).joinToString(" / ") { it.name }
                            TextButton(onClick = {
                                val toMove = photos.filter { it.id in selectedIds }
                                val targetDir = folderManager.getFolderDir(folder.id)
                                toMove.forEach { photo -> vault.moveToFolder(photo, targetDir) }
                                photos = photos.filter { it.id !in selectedIds }
                                selectedIds = emptySet(); isSelectionMode = false
                                if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
                                showMoveDialog = false
                                Toast.makeText(context, context.getString(R.string.moved_n_items_to_folder, toMove.size, folder.name), Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(path, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMoveDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Delete dialog
    if (showDeleteDialog) {
        val count = if (isSelectionMode) selectedIds.size else 1
        val isVideo = viewerPhoto?.mediaType == VaultMediaType.VIDEO
        val itemLabel = when {
            isSelectionMode && count > 1 -> stringResource(R.string.n_items, count)
            isVideo -> stringResource(R.string.video)
            else -> stringResource(R.string.photo)
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_item, itemLabel)) },
            text = { Text(stringResource(R.string.permanently_deleted_from_vault)) },
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
                }) { Text(stringResource(R.string.delete), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Merge face groups confirmation dialog
    mergeTargetGroup?.let { targetId ->
        val sourceId = mergeSourceGroup ?: return@let
        val sourceName = photoIndex?.getFaceGroupName(sourceId) ?: "Unknown"
        val targetName = photoIndex?.getFaceGroupName(targetId) ?: "Unknown"
        AlertDialog(
            onDismissRequest = { mergeTargetGroup = null; mergeSourceGroup = null },
            title = { Text("Merge Face Groups") },
            text = { Text("Merge \"$sourceName\" into \"$targetName\"? This combines both groups and helps the AI recognize this person better.") },
            confirmButton = {
                TextButton(onClick = {
                    photoIndex?.mergeFaceGroups(targetId, sourceId)
                    mergeTargetGroup = null
                    mergeSourceGroup = null
                    // Refresh face groups
                    val pi = photoIndex
                    if (pi != null) {
                        scope.launch {
                            val groups = withContext(Dispatchers.IO) { pi.getFaceGroups() }
                            faceGroups = groups
                        }
                    }
                }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { mergeTargetGroup = null; mergeSourceGroup = null }) { Text("Cancel") }
            }
        )
    }

    // Rename face group dialog
    renamingGroup?.let { groupId ->
        var newName by remember { mutableStateOf(photoIndex?.getFaceGroupName(groupId) ?: "") }
        AlertDialog(
            onDismissRequest = { renamingGroup = null },
            title = { Text("Name this person") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        photoIndex?.setFaceGroupName(groupId, newName.trim())
                    }
                    renamingGroup = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingGroup = null }) { Text("Cancel") }
            }
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
            VaultLockManager.activateDuress()
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
                isDuressActive = false
                VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked()
                categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                page = VaultPage.CATEGORIES
                pinInput = ""
                pinError = null
            }
            return
        }

        // Wrong PIN
        pinError = context.getString(R.string.incorrect_pin)
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
            categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
        }
    }

    // Auto-purge old trash items
    LaunchedEffect(Unit) {
        if (!isDuressActive) {
            withContext(Dispatchers.IO) { vault.autoPurgeTrash(); trashCount = vault.trashCount() }
        }
    }

    // Initialize Photo Intelligence index + classifier, then auto-index new photos
    LaunchedEffect(Unit) {
        if (!isDuressActive) {
            withContext(Dispatchers.IO) {
                try {
                    val c = ImageClassifier(context)
                    classifier = c
                    val fe = try { FaceEmbedder(context) } catch (_: Exception) { null }
                    val pi = PhotoIndex(PrivoraDatabase.getInstance(context, crypto))
                    photoIndex = pi

                    // Auto-index any unindexed photos in background
                    val allPhotos = getAllVaultItems().filter { it.mediaType == VaultMediaType.PHOTO }
                    val unindexed = allPhotos.filter { !pi.isIndexed(it.id) }
                    if (unindexed.isNotEmpty()) {
                        isIndexing = true
                        unindexed.forEachIndexed { i, photo ->
                            // Try full photo for face detection, fall back to thumbnail if OOM
                            val bmp = try {
                                // Skip full photo if encrypted file > 10MB (likely to OOM)
                                if (photo.encryptedFile.length() > 10 * 1024 * 1024) vault.loadThumbnail(photo)
                                else vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                            } catch (_: OutOfMemoryError) { vault.loadThumbnail(photo) }
                            bmp?.let { img ->
                                try {
                                    pi.indexPhoto(photo.id, img, c, faceEmbedder = fe)
                                } catch (_: Exception) {}
                                img.recycle()
                            }
                            indexProgress = i + 1 to unindexed.size
                        }
                        isIndexing = false
                    }
                    // Auto-name face groups from People profile photos
                    if (fe != null) {
                        try {
                            val contactRepo = com.privateai.camera.security.ContactRepository(
                                java.io.File(context.filesDir, "vault/contacts"), crypto, com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                            )
                            pi.autoNameFromContacts(contactRepo, fe)
                        } catch (_: Exception) {}
                    }
                    fe?.release()
                } catch (e: Exception) {
                    Log.e("VaultScreen", "Failed to init photo index: ${e.message}")
                }
            }
        }
    }

    // Auto-search when opened with a search query (e.g., from People → Photos)
    LaunchedEffect(initialSearchQuery, photoIndex, page) {
        if (initialSearchQuery.isNotBlank() && photoIndex != null && page == VaultPage.CATEGORIES && !isSearching) {
            val pi = photoIndex ?: return@LaunchedEffect
            isSearching = true
            scope.launch {
                val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                var matchedIds = withContext(Dispatchers.IO) { pi.searchByLabel(initialSearchQuery).toSet() }

                // If no results from label/name search, try profile photo face matching
                if (matchedIds.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            // initialSearchQuery contains "name contactId" — extract contactId
                            val parts = initialSearchQuery.split(" ")
                            val contactId = parts.lastOrNull()?.takeIf { it.length > 10 } // UUIDs are long
                            if (contactId != null) {
                                val contactRepo = com.privateai.camera.security.ContactRepository(java.io.File(context.filesDir, "vault/contacts"), crypto, com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto))
                                val profileBmp = contactRepo.loadProfilePhoto(contactId)
                                if (profileBmp != null) {
                                    val fe = com.privateai.camera.bridge.FaceEmbedder(context)
                                    val embeddings = fe.detectAndEmbed(profileBmp)
                                    profileBmp.recycle()
                                    if (embeddings.isNotEmpty()) {
                                        matchedIds = pi.findPhotosByFaceEmbedding(embeddings[0].second).toSet()
                                    }
                                    fe.release()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                searchResults = allItems.filter { it.id in matchedIds }
                val thumbMap = mutableMapOf<String, Bitmap>()
                withContext(Dispatchers.IO) {
                    searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                }
                searchThumbnails = thumbMap
            }
        }
    }

    when (page) {
        VaultPage.LOCKED -> {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(R.string.encrypted_vault)) }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                })
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.vault_is_locked), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

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
                            label = { Text(stringResource(R.string.enter_pin)) },
                            modifier = Modifier.width(200.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pinError != null,
                            supportingText = {
                                pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        )

                        Button(
                            onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                            enabled = pinInput.length >= 4,
                            modifier = Modifier.width(200.dp)
                        ) { Text(stringResource(R.string.unlock)) }
                    } else {
                        // Phone lock mode: biometric/device credential only
                        Button(
                            onClick = { authenticate() },
                            modifier = Modifier.width(200.dp)
                        ) { Text(stringResource(R.string.unlock)) }
                    }
                }
            }
        }

        VaultPage.CATEGORIES -> {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(R.string.encrypted_vault)) }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = {
                        if (showFaceGroups) {
                            if (selectedFaceGroup != null) {
                                selectedFaceGroup = null
                            } else {
                                showFaceGroups = false
                            }
                        } else if (isSearching || smartMode != null) {
                            if (searchFromViewer) {
                                // Return to the photo viewer
                                searchFromViewer = false
                                page = VaultPage.VIEWER
                            }
                            searchQuery = ""; isSearching = false; smartMode = null
                            searchResults = emptyList(); searchThumbnails = emptyMap()
                            searchSuggestions = emptyList()
                        } else {
                            onBack()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                })
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            smartMode = null
                            searchFromViewer = false
                            if (isDuressActive) { isSearching = false; searchResults = emptyList(); return@OutlinedTextField }
                            // Auto-suggest from known labels + aliases
                            if (query.isNotEmpty()) {
                                val q = query.lowercase()
                                val aliasKeys = photoIndex?.let {
                                    it.getAllLabels().filter { l -> l.lowercase().startsWith(q) }
                                } ?: emptyList()
                                val aliasMatches = listOf("person", "people", "selfie", "face", "food", "animal", "car", "flower", "building", "beach", "nature", "sky", "tree", "baby", "sport", "phone", "laptop", "book", "cat", "dog")
                                    .filter { it.startsWith(q) && it != q }
                                searchSuggestions = (aliasMatches + aliasKeys).distinct().take(5)
                            } else {
                                searchSuggestions = emptyList()
                            }
                            if (query.length >= 2) {
                                isSearching = true
                                scope.launch {
                                    val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                                    val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    searchResults = allItems.filter { item ->
                                        item.id.contains(query, ignoreCase = true) ||
                                        item.mediaType.name.contains(query, ignoreCase = true) ||
                                        item.category.label.contains(query, ignoreCase = true) ||
                                        dateFmt.format(java.util.Date(item.timestamp)).contains(query)
                                    }
                                    // Also search by AI labels if index is available
                                    val labelMatches = photoIndex?.searchByLabel(query)?.toSet() ?: emptySet()
                                    if (labelMatches.isNotEmpty()) {
                                        val labelPhotos = allItems.filter { it.id in labelMatches }
                                        searchResults = (searchResults + labelPhotos).distinctBy { it.id }
                                    }
                                    // Load thumbnails for search results
                                    val thumbMap = searchThumbnails.toMutableMap()
                                    withContext(Dispatchers.IO) {
                                        searchResults.forEach { photo ->
                                            if (photo.id !in thumbMap) {
                                                vault.loadThumbnail(photo)?.let { thumbMap[photo.id] = it }
                                            }
                                        }
                                    }
                                    searchThumbnails = thumbMap
                                }
                            } else {
                                isSearching = false
                                searchResults = emptyList()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_vault)) },
                        leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.search)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearching = false
                                    searchResults = emptyList()
                                    smartMode = null
                                }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true
                    )

                    // Auto-suggest
                    if (searchSuggestions.isNotEmpty() && searchQuery.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            searchSuggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = {
                                        searchQuery = suggestion
                                        searchSuggestions = emptyList()
                                        // Trigger search
                                        isSearching = true
                                        scope.launch {
                                            val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                                            val labelMatches = photoIndex?.searchByLabel(suggestion)?.toSet() ?: emptySet()
                                            searchResults = allItems.filter { it.id in labelMatches }
                                            val thumbMap = mutableMapOf<String, Bitmap>()
                                            withContext(Dispatchers.IO) {
                                                searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                            }
                                            searchThumbnails = thumbMap
                                        }
                                    },
                                    label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }

                    // Smart search chips (hidden during duress)
                    if (!isDuressActive) Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pi = photoIndex
                        if (pi != null) {
                            val indexedCount = pi.getIndexedCount()
                            val totalPhotos = getAllVaultItems().count { it.mediaType == VaultMediaType.PHOTO }

                            if (indexedCount < totalPhotos && !isIndexing) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        isIndexing = true
                                        scope.launch(Dispatchers.IO) {
                                            val allPhotos = getAllVaultItems().filter { it.mediaType == VaultMediaType.PHOTO }
                                            val cl = classifier ?: return@launch
                                            val fe = try { FaceEmbedder(context) } catch (_: Exception) { null }
                                            allPhotos.forEachIndexed { i, photo ->
                                                if (!pi.isIndexed(photo.id)) {
                                                    val bmp = vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                                                    bmp?.let { img ->
                                                        pi.indexPhoto(photo.id, img, cl, faceEmbedder = fe)
                                                        img.recycle()
                                                    }
                                                }
                                                indexProgress = i + 1 to allPhotos.size
                                            }
                                            fe?.release()
                                            isIndexing = false
                                        }
                                    },
                                    label = { Text("Index Photos ($indexedCount/$totalPhotos)") }
                                )
                            }

                            if (isIndexing) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("Indexing... ${indexProgress.first}/${indexProgress.second}") }
                                )
                            }

                            if (indexedCount > 0) {
                                FilterChip(
                                    selected = smartMode == "duplicates",
                                    onClick = {
                                        showFaceGroups = false; selectedFaceGroup = null
                                        if (smartMode == "duplicates") {
                                            smartMode = null
                                            isSearching = false
                                        } else {
                                            smartMode = "duplicates"
                                            duplicateGroups = pi.findDuplicates()
                                            val dupIds = duplicateGroups.flatten().toSet()
                                            val allPhotos = getAllVaultItems()
                                            searchResults = allPhotos.filter { it.id in dupIds }
                                            isSearching = true
                                            scope.launch {
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                withContext(Dispatchers.IO) {
                                                    searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                                }
                                                searchThumbnails = thumbMap
                                            }
                                        }
                                    },
                                    label = { Text("Duplicates") }
                                )

                                FilterChip(
                                    selected = smartMode == "blurry",
                                    onClick = {
                                        showFaceGroups = false; selectedFaceGroup = null
                                        if (smartMode == "blurry") {
                                            smartMode = null
                                            isSearching = false
                                        } else {
                                            smartMode = "blurry"
                                            val blurryIds = pi.findBlurry().toSet()
                                            val allPhotos = getAllVaultItems()
                                            searchResults = allPhotos.filter { it.id in blurryIds }
                                            isSearching = true
                                            scope.launch {
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                withContext(Dispatchers.IO) {
                                                    searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                                }
                                                searchThumbnails = thumbMap
                                            }
                                        }
                                    },
                                    label = { Text("Blurry") }
                                )

                                FilterChip(
                                    selected = showFaceGroups,
                                    onClick = {
                                        if (showFaceGroups) {
                                            showFaceGroups = false
                                            selectedFaceGroup = null
                                        } else {
                                            val pi = photoIndex ?: return@FilterChip
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val groups = pi.getFaceGroups()
                                                    // Auto-name groups from People profile photos
                                                    try {
                                                        val contactRepo = com.privateai.camera.security.ContactRepository(
                                                            java.io.File(context.filesDir, "vault/contacts"), crypto, com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                                                        )
                                                        val fe = com.privateai.camera.bridge.FaceEmbedder(context)
                                                        pi.autoNameFromContacts(contactRepo, fe)
                                                        fe.release()
                                                    } catch (_: Exception) {}
                                                    faceGroups = groups
                                                }
                                                showFaceGroups = true
                                                isSearching = false
                                                smartMode = null
                                            }
                                        }
                                    },
                                    label = { Text("\uD83D\uDC64 Faces") }
                                )
                            }
                        }
                    }

                    if (showFaceGroups && selectedFaceGroup == null) {
                        // Load thumbnails for face group avatars
                        var faceGroupThumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
                        LaunchedEffect(faceGroups) {
                            val thumbs = mutableMapOf<String, Bitmap>()
                            withContext(Dispatchers.IO) {
                                faceGroups.forEach { (groupId, members) ->
                                    val firstPhotoId = members.firstOrNull()?.first ?: return@forEach
                                    val photo = getAllVaultItems().find { it.id == firstPhotoId } ?: return@forEach
                                    vault.loadThumbnail(photo)?.let { thumbs[groupId] = it }
                                }
                            }
                            faceGroupThumbs = thumbs
                        }
                        // Show face groups grid
                        if (faceGroups.isEmpty()) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("No face groups found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Index more photos to detect faces", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            if (mergeSourceGroup != null) {
                                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tap another group to merge", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    TextButton(onClick = { mergeSourceGroup = null }) { Text("Cancel") }
                                }
                            } else {
                                Text("${faceGroups.size} groups  •  Long press to merge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                faceGroups.forEach { (groupId, members) ->
                                    item {
                                        val groupName = photoIndex?.getFaceGroupName(groupId)
                                        val photoCount = members.map { it.first }.distinct().size
                                        val firstThumb = faceGroupThumbs[groupId]

                                        Card(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .then(if (mergeSourceGroup == groupId) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (mergeSourceGroup != null && mergeSourceGroup != groupId) {
                                                            // Second group tapped — confirm merge
                                                            mergeTargetGroup = groupId
                                                        } else if (mergeSourceGroup == groupId) {
                                                            // Cancel merge mode
                                                            mergeSourceGroup = null
                                                        } else {
                                                            // Normal: show photos
                                                            selectedFaceGroup = groupId
                                                            val photoIds = members.map { it.first }.distinct().toSet()
                                                            val allPhotos = getAllVaultItems()
                                                            searchResults = allPhotos.filter { it.id in photoIds }
                                                            scope.launch {
                                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                                withContext(Dispatchers.IO) {
                                                                    searchResults.forEach { p ->
                                                                        vault.loadThumbnail(p)?.let { thumbMap[p.id] = it }
                                                                    }
                                                                }
                                                                searchThumbnails = thumbMap
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (mergeSourceGroup == null) {
                                                            mergeSourceGroup = groupId
                                                        } else {
                                                            renamingGroup = groupId
                                                        }
                                                    }
                                                )
                                        ) {
                                            Column(
                                                Modifier.fillMaxSize().padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                // Face avatar circle
                                                if (firstThumb != null) {
                                                    Image(
                                                        firstThumb.asImageBitmap(),
                                                        "Face group",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Face, "Face group", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    groupName ?: "Unknown",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    "$photoCount photos",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (showFaceGroups && selectedFaceGroup != null) {
                        // Show photos for selected face group (sorted newest first)
                        val groupName = photoIndex?.getFaceGroupName(selectedFaceGroup!!) ?: "Unknown Person"
                        val sortedResults = remember(searchResults) { searchResults.sortedByDescending { it.timestamp } }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(groupName, style = MaterialTheme.typography.titleSmall)
                                Text("${sortedResults.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelectionMode && selectedIds.isNotEmpty()) {
                                    Text("${selectedIds.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 4.dp))
                                    // Remove from group (doesn't delete the photo)
                                    IconButton(onClick = {
                                        selectedFaceGroup?.let { gId ->
                                            photoIndex?.removeFromFaceGroup(gId, selectedIds)
                                            searchResults = searchResults.filter { it.id !in selectedIds }
                                            // Refresh faceGroups so re-entering shows updated data
                                            val pi = photoIndex
                                            if (pi != null) {
                                                scope.launch {
                                                    faceGroups = withContext(Dispatchers.IO) { pi.getFaceGroups() }
                                                }
                                            }
                                        }
                                        selectedIds = emptySet(); isSelectionMode = false
                                    }) {
                                        Icon(Icons.Default.Close, "Remove from group", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    // Delete photo permanently
                                    IconButton(onClick = { showDeleteDialog = true }) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) {
                                        Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                                    }
                                }
                                IconButton(onClick = { selectedFaceGroup = null; selectedIds = emptySet(); isSelectionMode = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to groups")
                                }
                            }
                        }
                        // Google Photos-style staggered grid
                        val faceGridWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 24.dp
                        val maxItemHeight = 160f // cap max height
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            // Build rows: pack photos into rows of 3, using real aspect ratios
                            val photosWithAspect = sortedResults.map { photo ->
                                val thumb = searchThumbnails[photo.id]
                                val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                photo to aspect
                            }
                            val rows = photosWithAspect.chunked(3)

                            items(rows) { row ->
                                val totalAspect = row.sumOf { it.second.toDouble() }.toFloat()
                                val gaps = (row.size - 1) * 3f
                                val rowHeight = ((faceGridWidth.value - gaps) / totalAspect).coerceAtMost(maxItemHeight)
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    row.forEach { (photo, aspect) ->
                                        val itemWidth = (rowHeight * aspect).dp
                                        val itemHeight = rowHeight.dp
                                        val thumb = searchThumbnails[photo.id]
                                        val isSelected = photo.id in selectedIds
                                        Box(
                                            Modifier.width(itemWidth).height(itemHeight)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                                        } else {
                                                            photos = sortedResults; thumbnails = searchThumbnails; openViewer(photo)
                                                        }
                                                    },
                                                    onLongClick = { isSelectionMode = true; selectedIds = selectedIds + photo.id }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumb != null) {
                                                Image(thumb.asImageBitmap(), "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Icon(Icons.Default.Lock, "Encrypted", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (isSelectionMode && isSelected) {
                                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isSearching) {
                        // Search results view
                        if (searchResults.isEmpty()) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(stringResource(R.string.no_results_found), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.n_results, searchResults.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelectionMode && selectedIds.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${selectedIds.size}", style = MaterialTheme.typography.titleSmall)
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                        }
                                        IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) {
                                            Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                                        }
                                    }
                                }
                            }
                            val searchGridWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 24.dp
                            LazyColumn(contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val searchRows = searchResults.chunked(3)
                                items(searchRows) { row ->
                                    val totalAspect = row.sumOf {
                                        val t = searchThumbnails[it.id]
                                        (if (t != null && t.height > 0) t.width.toFloat() / t.height else if (it.mediaType == VaultMediaType.PDF) 0.75f else 1.33f).toDouble()
                                    }.toFloat()
                                    val gaps = (row.size - 1) * 3f
                                    val rowHeight = ((searchGridWidth.value - gaps) / totalAspect).coerceIn(80f, 160f)
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        row.forEach { photo ->
                                            val thumb = searchThumbnails[photo.id]
                                            val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                            val isSelected = photo.id in selectedIds
                                            Box(
                                                Modifier.width((rowHeight * aspect).dp).height(rowHeight.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                                            } else { photos = searchResults; thumbnails = searchThumbnails; openViewer(photo) }
                                                        },
                                                        onLongClick = { isSelectionMode = true; selectedIds = selectedIds + photo.id }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (photo.mediaType == VaultMediaType.PDF) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                        Icon(Icons.Default.PictureAsPdf, stringResource(R.string.cd_pdf_document), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                                        Text(photo.id.let { if (it.length > 15) it.take(12) + "..." else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                    }
                                                } else if (thumb != null) {
                                                    Image(thumb.asImageBitmap(), if (photo.mediaType == VaultMediaType.VIDEO) stringResource(R.string.cd_video_thumbnail) else stringResource(R.string.cd_photo_thumbnail), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Icon(Icons.Default.Lock, stringResource(R.string.cd_encrypted_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (photo.mediaType == VaultMediaType.VIDEO) {
                                                    Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter)
                                                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
                                                    Icon(Icons.Default.PlayCircleFilled, stringResource(R.string.video), tint = Color.White.copy(alpha = 0.9f),
                                                        modifier = Modifier.size(40.dp).align(Alignment.Center))
                                                }
                                                if (isSelectionMode && isSelected) {
                                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Normal categories view
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val halfWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 48.dp) / 2
                            val allFolderItems = if (!isDuressActive) folderManager.listAllFolders().flatMap { vault.listFolderItems(folderManager.getFolderDir(it.id)) } else emptyList()
                            val allPhotosCount = if (!isDuressActive) vault.listAllPhotosOnly(allFolderItems).size else 0
                            val allVideosCount = if (!isDuressActive) vault.listAllVideosOnly(allFolderItems).size else 0
                            CompactCategoryCard(stringResource(R.string.category_camera), categoryCounts[VaultCategory.CAMERA] ?: 0, Icons.Default.CameraAlt, halfWidth) { openCategory(VaultCategory.CAMERA) }
                            // Photos (virtual smart view)
                            CompactCategoryCard(stringResource(R.string.all_photos), allPhotosCount, Icons.Default.Photo, halfWidth) {
                                isVirtualPhotos = true
                                isVirtualVideos = false
                                scope.launch {
                                    val allFolderItems = withContext(Dispatchers.IO) {
                                        folderManager.listAllFolders().flatMap { folder ->
                                            vault.listFolderItems(folderManager.getFolderDir(folder.id))
                                        }
                                    }
                                    photos = withContext(Dispatchers.IO) { vault.listAllPhotosOnly(allFolderItems) }
                                    thumbnails = emptyMap()
                                    currentCategory = VaultCategory.CAMERA
                                    page = VaultPage.GALLERY
                                    scope.launch {
                                        val thumbMap = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                        thumbnails = thumbMap
                                    }
                                }
                            }
                            // Videos (virtual smart view)
                            CompactCategoryCard(stringResource(R.string.all_videos), allVideosCount, Icons.Default.Videocam, halfWidth) {
                                isVirtualVideos = true
                                isVirtualPhotos = false
                                scope.launch {
                                    val allFolderItems = withContext(Dispatchers.IO) {
                                        folderManager.listAllFolders().flatMap { folder ->
                                            vault.listFolderItems(folderManager.getFolderDir(folder.id))
                                        }
                                    }
                                    photos = withContext(Dispatchers.IO) { vault.listAllVideosOnly(allFolderItems) }
                                    thumbnails = emptyMap()
                                    currentCategory = VaultCategory.VIDEO
                                    page = VaultPage.GALLERY
                                    scope.launch {
                                        val thumbMap = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                        thumbnails = thumbMap
                                    }
                                }
                            }
                            CompactCategoryCard(stringResource(R.string.category_scans), categoryCounts[VaultCategory.SCAN] ?: 0, Icons.Default.DocumentScanner, halfWidth) { openCategory(VaultCategory.SCAN) }
                            CompactCategoryCard(stringResource(R.string.category_detections), categoryCounts[VaultCategory.DETECT] ?: 0, Icons.Default.CameraAlt, halfWidth) { openCategory(VaultCategory.DETECT) }
                            CompactCategoryCard(stringResource(R.string.category_reports), categoryCounts[VaultCategory.REPORTS] ?: 0, Icons.Default.Description, halfWidth) { openCategory(VaultCategory.REPORTS) }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        // My Folders section
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.my_folders), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, stringResource(R.string.new_folder), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }

                        if (rootFolders.isEmpty()) {
                            Text(stringResource(R.string.no_folders_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                        }

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val halfW = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 48.dp) / 2
                        rootFolders.forEach { folder ->
                            val itemCount = folderManager.countItems(folder.id)
                            CompactCategoryCard(folder.name, itemCount, Icons.Default.Description, halfW) {
                                currentFolder = folder
                                val dir = folderManager.getFolderDir(folder.id)
                                photos = vault.listFolderItems(dir)
                                thumbnails = emptyMap()
                                scope.launch {
                                    val thumbMap = mutableMapOf<String, Bitmap>()
                                    withContext(Dispatchers.IO) {
                                        photos.forEach { photo ->
                                            vault.loadThumbnail(photo)?.let { thumbMap[photo.id] = it }
                                        }
                                    }
                                    thumbnails = thumbMap
                                    subfolders = folderManager.listSubfolders(folder.id)
                                    page = VaultPage.FOLDER_VIEW
                                }
                            }
                        }
                        } // end FlowRow

                        // Trash section
                        if (trashCount > 0) {
                            Spacer(Modifier.height(16.dp))
                            Card(
                                Modifier.fillMaxWidth().clickable {
                                    trashItems = vault.listTrash()
                                    // Load trash thumbnails
                                    scope.launch {
                                        val thumbs = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) {
                                            trashItems.forEach { item ->
                                                if (item.thumbnailFile.exists()) {
                                                    try {
                                                        val bytes = crypto.decryptFile(item.thumbnailFile)
                                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { thumbs[item.id] = it }
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                        trashThumbnails = thumbs
                                    }
                                    page = VaultPage.TRASH
                                },
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Delete, "Trash", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                                    Column(Modifier.weight(1f)) {
                                        Text("Trash", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        Text("$trashCount item${if (trashCount > 1) "s" else ""} • auto-deletes after 30 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } // end if/else isSearching
                }
            }
        }

        VaultPage.GALLERY -> {
            Scaffold(topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.n_selected, selectedIds.size)) },
                        navigationIcon = { IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) } },
                        actions = {
                            if (selectedIds.size == 1) {
                                IconButton(onClick = {
                                    viewerPhoto = photos.find { it.id in selectedIds }
                                    showDetailsDialog = true
                                }) { Icon(Icons.Default.Info, stringResource(R.string.details)) }
                            }
                            if (!isVirtualPhotos && !isVirtualVideos) {
                                IconButton(onClick = { showMoveDialog = true }) { Icon(Icons.Default.DriveFileMove, stringResource(R.string.move)) }
                            }
                            IconButton(onClick = { shareImages(selectedIds) }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                            IconButton(onClick = { saveToDevice(selectedIds) }) { Icon(Icons.Default.SaveAlt, stringResource(R.string.save)) }
                            IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                when {
                                    isVirtualPhotos -> "${stringResource(R.string.all_photos)} (${photos.size})"
                                    isVirtualVideos -> "${stringResource(R.string.all_videos)} (${photos.size})"
                                    else -> stringResource(R.string.category_with_count, currentCategory.label, photos.size)
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                // thumbnails cleared — GC handles bitmap recycling (Compose may still be drawing)
                                thumbnails = emptyMap()
                                isVirtualPhotos = false
                                isVirtualVideos = false
                                if (!isDuressActive) categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                                page = VaultPage.CATEGORIES
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        }
                    )
                }
            }) { padding ->
                if (photos.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.no_items_yet), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val grouped = remember(photos) { groupPhotosByDate(photos) }

                    val galleryGridWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 16.dp
                    LazyColumn(
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
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
                            // Build rows: separate videos (2 per row, full width) from photos (3/2 alternating)
                            val isVideoCategory = isVirtualVideos || currentCategory == VaultCategory.VIDEO
                            val isScanCategory = currentCategory == VaultCategory.SCAN && !isVirtualPhotos && !isVirtualVideos

                            // Split into typed rows: videos always 2-per-row full width, photos use normal layout
                            data class TypedRow(val items: List<VaultPhoto>, val isVideoRow: Boolean)
                            val typedRows = if (isVideoCategory) {
                                groupPhotos.chunked(2).map { TypedRow(it, true) }
                            } else if (isScanCategory) {
                                groupPhotos.chunked(3).map { TypedRow(it, false) }
                            } else {
                                // Build rows preserving chronological order, grouping consecutive same-type items
                                val rows = mutableListOf<TypedRow>()
                                var currentPhotoBatch = mutableListOf<VaultPhoto>()
                                var currentVideoBatch = mutableListOf<VaultPhoto>()

                                fun flushPhotos() {
                                    if (currentPhotoBatch.isEmpty()) return
                                    var i2 = 0; var ri = 0
                                    while (i2 < currentPhotoBatch.size) {
                                        val count = if (ri % 3 == 1) 2 else 3
                                        rows.add(TypedRow(currentPhotoBatch.subList(i2, (i2 + count).coerceAtMost(currentPhotoBatch.size)), false))
                                        i2 += count; ri++
                                    }
                                    currentPhotoBatch = mutableListOf()
                                }
                                fun flushVideos() {
                                    if (currentVideoBatch.isEmpty()) return
                                    currentVideoBatch.chunked(2).forEach { rows.add(TypedRow(it, true)) }
                                    currentVideoBatch = mutableListOf()
                                }

                                // Items are already sorted by timestamp desc
                                groupPhotos.forEach { item ->
                                    if (item.mediaType == VaultMediaType.VIDEO) {
                                        flushPhotos()
                                        currentVideoBatch.add(item)
                                    } else {
                                        flushVideos()
                                        currentPhotoBatch.add(item)
                                    }
                                }
                                flushPhotos()
                                flushVideos()
                                rows
                            }
                            typedRows.forEach { typedRow ->
                                val row = typedRow.items
                                item {
                                    val isVidRow = typedRow.isVideoRow
                                    val totalAspect = row.sumOf {
                                        val t = thumbnails[it.id]
                                        val a = if (t != null && t.height > 0) t.width.toFloat() / t.height else if (it.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                        a.toDouble()
                                    }.toFloat()
                                    val gaps = (row.size - 1) * 3f
                                    val maxRowH = if (isVidRow) 220f else 160f
                                    val rowHeight = ((galleryGridWidth.value - gaps) / totalAspect).coerceIn(80f, maxRowH)
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                        row.forEach { photo ->
                                            val thumb = thumbnails[photo.id]
                                            val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                            val isSelected = photo.id in selectedIds
                                            val fillEqual = isVidRow || isScanCategory
                                            val equalWidth = (galleryGridWidth.value - (row.size - 1) * 3f) / row.size
                                            val equalHeight = (equalWidth / (if (isVidRow) 1.78f else 0.75f)).dp
                                            Box(
                                                Modifier.then(if (fillEqual) Modifier.weight(1f).height(equalHeight) else Modifier.width((rowHeight * aspect).dp).height(rowHeight.dp))
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
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
                                                if (photo.mediaType == VaultMediaType.PDF) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                        Icon(Icons.Default.PictureAsPdf, stringResource(R.string.cd_pdf_document), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                                        Text(photo.id.let { if (it.length > 15) it.take(12) + "..." else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                    }
                                                } else if (thumb != null) {
                                                    Image(thumb.asImageBitmap(), if (photo.mediaType == VaultMediaType.VIDEO) stringResource(R.string.cd_video_thumbnail) else stringResource(R.string.cd_photo_thumbnail), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Icon(Icons.Default.Lock, stringResource(R.string.cd_encrypted_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (photo.mediaType == VaultMediaType.VIDEO) {
                                                    Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter)
                                                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
                                                    Icon(Icons.Default.PlayCircleFilled, stringResource(R.string.video), tint = Color.White.copy(alpha = 0.9f),
                                                        modifier = Modifier.size(40.dp).align(Alignment.Center))
                                                }
                                                if (isSelectionMode && isSelected) {
                                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
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
        }

        VaultPage.VIEWER -> {
            // Get navigable items (photos + videos, not PDFs)
            val viewablePhotos = remember(photos) { photos.filter { it.mediaType != VaultMediaType.PDF } }
            val currentIndex = viewablePhotos.indexOfFirst { it.id == viewerPhoto?.id }

            fun navigateToItem(index: Int) {
                val item = viewablePhotos.getOrNull(index) ?: return
                if (item.mediaType == VaultMediaType.VIDEO) {
                    // Switch to video player
                    scope.launch {
                        val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(item) }
                        if (tempFile != null) {
                            // viewerBitmap cleared — GC handles recycling
                            viewerBitmap = null
                            videoTempFile = tempFile
                            viewerPhoto = item
                            page = VaultPage.VIDEO_PLAYER
                        }
                    }
                } else {
                    scope.launch {
                        val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(item) }
                        // viewerBitmap cleared — GC handles recycling
                        viewerBitmap = bmp
                        viewerPhoto = item
                    }
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                viewerBitmap?.let { bmp ->
                    var dragTotal by remember { mutableStateOf(0f) }
                    Image(
                        bmp.asImageBitmap(), stringResource(R.string.photo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentIndex) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragTotal = 0f },
                                    onDragEnd = {
                                        if (dragTotal > 100 && currentIndex > 0) {
                                            navigateToItem(currentIndex - 1) // swipe right = prev
                                        } else if (dragTotal < -100 && currentIndex < viewablePhotos.size - 1) {
                                            navigateToItem(currentIndex + 1) // swipe left = next
                                        }
                                        // First/last item: do nothing on swipe
                                    },
                                    onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount }
                                )
                            }
                    )
                }

                // Counter
                if (viewablePhotos.size > 1 && currentIndex >= 0) {
                    Text(
                        "${currentIndex + 1} / ${viewablePhotos.size}",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 54.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = { viewerBitmap = null; page = VaultPage.GALLERY },
                    Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 16.dp).size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }

                val blurDefault = com.privateai.camera.ui.settings.isFaceBlurEnabled(context)

                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share (respects global setting)
                    IconButton(onClick = { viewerPhoto?.let { sharePhoto(it) } }) {
                        Icon(Icons.Default.Share, stringResource(R.string.share), tint = Color.White)
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
                                        val label = if (blurDefault) context.getString(R.string.share_no_blur) else context.getString(R.string.share_faces_blurred)
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
                            if (blurDefault) stringResource(R.string.share_without_blur) else stringResource(R.string.blur_and_share),
                            tint = if (blurDefault) Color.White else Color(0xFF4CAF50)
                        )
                    }
                    // Edit (photos only)
                    if (viewerPhoto?.mediaType == VaultMediaType.PHOTO) {
                        IconButton(onClick = {
                            viewerPhoto?.let { photo ->
                                viewerBitmap?.let { bmp ->
                                    showEditor = true
                                    editorPhoto = photo
                                    editorBitmap = bmp
                                }
                            }
                        }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = Color.White)
                        }
                    }
                    // Find Similar button
                    if (photoIndex != null && viewerPhoto != null) {
                        IconButton(onClick = {
                            val pi = photoIndex ?: return@IconButton
                            val vp = viewerPhoto ?: return@IconButton
                            scope.launch {
                                val similarIds = withContext(Dispatchers.IO) { pi.findSimilar(vp.id).map { it.first }.toSet() }
                                if (similarIds.isNotEmpty()) {
                                    val allPhotos = withContext(Dispatchers.IO) { getAllVaultItems() }
                                    searchResults = allPhotos.filter { it.id in similarIds }
                                    isSearching = true
                                    smartMode = null
                                    searchFromViewer = true
                                    page = VaultPage.CATEGORIES
                                    val thumbMap = mutableMapOf<String, Bitmap>()
                                    withContext(Dispatchers.IO) {
                                        searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                    }
                                    searchThumbnails = thumbMap
                                } else {
                                    Toast.makeText(context, context.getString(R.string.no_similar_found), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Search, stringResource(R.string.find_similar), tint = Color.White)
                        }
                    }
                    // Details
                    IconButton(onClick = { showDetailsDialog = true }) {
                        Icon(Icons.Default.Info, stringResource(R.string.details), tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color(0xFFFF6B6B))
                    }
                }

                // AI labels (above action bar, pass-through touches)
                viewerPhoto?.let { vp ->
                    photoIndex?.getLabels(vp.id)?.let { labels ->
                        if (labels.isNotEmpty()) {
                            Row(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                labels.forEach { label ->
                                    SuggestionChip(onClick = {
                                        searchQuery = label
                                        searchFromViewer = true
                                        scope.launch {
                                            val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                                            val labelMatches = photoIndex?.searchByLabel(label)?.toSet() ?: emptySet()
                                            searchResults = allItems.filter { it.id in labelMatches }
                                            isSearching = true
                                            smartMode = null
                                            page = VaultPage.CATEGORIES
                                            val thumbMap = mutableMapOf<String, Bitmap>()
                                            withContext(Dispatchers.IO) {
                                                searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                            }
                                            searchThumbnails = thumbMap
                                        }
                                    }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        }
                    }
                }
            }
        }

        VaultPage.VIDEO_PLAYER -> {
            val viewablePhotos = remember(photos) { photos.filter { it.mediaType != VaultMediaType.PDF } }
            val currentVideoIndex = viewablePhotos.indexOfFirst { it.id == viewerPhoto?.id }

            fun navigateVideo(index: Int) {
                val item = viewablePhotos.getOrNull(index) ?: return
                videoTempFile?.delete()
                videoTempFile = null
                if (item.mediaType == VaultMediaType.VIDEO) {
                    scope.launch {
                        val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(item) }
                        if (tempFile != null) {
                            videoTempFile = tempFile
                            viewerPhoto = item
                        }
                    }
                } else {
                    // Switch to photo viewer
                    scope.launch {
                        val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(item) }
                        viewerBitmap = bmp
                        viewerPhoto = item
                        page = VaultPage.VIEWER
                    }
                }
            }

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
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }

                // Counter
                if (viewablePhotos.size > 1 && currentVideoIndex >= 0) {
                    Text(
                        "${currentVideoIndex + 1} / ${viewablePhotos.size}",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 54.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Prev/Next arrows on sides
                if (currentVideoIndex > 0) {
                    IconButton(
                        onClick = { navigateVideo(currentVideoIndex - 1) },
                        Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                            .size(40.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous), tint = Color.White) }
                }
                if (currentVideoIndex < viewablePhotos.size - 1) {
                    IconButton(
                        onClick = { navigateVideo(currentVideoIndex + 1) },
                        Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                            .size(40.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.next), tint = Color.White,
                            modifier = Modifier.graphicsLayer(scaleX = -1f)
                        )
                    }
                }

                // Bottom bar: share + save + info + delete
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
                                            }, context.getString(R.string.share_video)
                                        ))
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, stringResource(R.string.share), tint = Color.White)
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
                                            Toast.makeText(context, context.getString(R.string.video_saved_to_gallery), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.SaveAlt, stringResource(R.string.save_to_device), tint = Color.White)
                    }
                    // Details
                    IconButton(onClick = { showDetailsDialog = true }) {
                        Icon(Icons.Default.Info, stringResource(R.string.details), tint = Color.White)
                    }
                    // Delete
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color(0xFFFF6B6B))
                    }
                }
            }
        }

        VaultPage.FOLDER_VIEW -> {
            val folder = currentFolder
            if (folder == null) { page = VaultPage.CATEGORIES } else {
            val breadcrumb = remember(folder.id) { folderManager.getFolderPath(folder.id) }

            Scaffold(topBar = {
                TopAppBar(
                    title = { Text(folder.name) },
                    navigationIcon = {
                        IconButton(onClick = {
                            // thumbnails cleared — GC handles bitmap recycling (Compose may still be drawing)
                            thumbnails = emptyMap()
                            if (folder.parentId != null) {
                                // Go up to parent folder
                                val parent = folderManager.getFolder(folder.parentId)
                                if (parent != null) {
                                    currentFolder = parent
                                    val dir = folderManager.getFolderDir(parent.id)
                                    photos = vault.listFolderItems(dir)
                                    subfolders = folderManager.listSubfolders(parent.id)
                                    scope.launch {
                                        val thumbMap = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                        thumbnails = thumbMap
                                    }
                                } else {
                                    currentFolder = null
                                    rootFolders = folderManager.listRootFolders()
                                    page = VaultPage.CATEGORIES
                                }
                            } else {
                                currentFolder = null
                                rootFolders = folderManager.listRootFolders()
                                page = VaultPage.CATEGORIES
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        IconButton(onClick = {
                            importLauncher.launch(arrayOf("image/*", "video/*", "application/pdf"))
                        }) { Icon(Icons.Default.Add, stringResource(R.string.import_to_folder)) }
                        IconButton(onClick = { showCreateFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_subfolder)) }
                        IconButton(onClick = { showRenameFolderDialog = true }) { Icon(Icons.Default.Edit, stringResource(R.string.rename)) }
                        IconButton(onClick = { showDeleteFolderDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                    }
                )
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // Breadcrumb
                    if (breadcrumb.size > 1) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            breadcrumb.forEachIndexed { i, f ->
                                if (i > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(
                                    f.name,
                                    color = if (f.id == folder.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        if (f.id != folder.id) {
                                            currentFolder = f
                                            val dir = folderManager.getFolderDir(f.id)
                                            photos = vault.listFolderItems(dir)
                                            subfolders = folderManager.listSubfolders(f.id)
                                            scope.launch {
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                                thumbnails = thumbMap
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Subfolders (compact 2-column grid)
                    if (subfolders.isNotEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val halfW = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 40.dp) / 2
                            subfolders.forEach { sub ->
                                val itemCount = folderManager.countItems(sub.id)
                                CompactCategoryCard(sub.name, itemCount, Icons.Default.Folder, halfW) {
                                    currentFolder = sub
                                    val dir = folderManager.getFolderDir(sub.id)
                                    photos = vault.listFolderItems(dir)
                                    subfolders = folderManager.listSubfolders(sub.id)
                                    thumbnails = emptyMap()
                                    scope.launch {
                                        val thumbMap = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                        thumbnails = thumbMap
                                    }
                                }
                            }
                        }
                    }

                    // Items in this folder (same grid as GALLERY)
                    if (photos.isEmpty() && subfolders.isEmpty()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(stringResource(R.string.empty_folder), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (photos.isNotEmpty()) {
                        val grouped = remember(photos) { groupPhotosByDate(photos) }
                        val folderGridW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 16.dp
                        LazyColumn(contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            grouped.forEach { (header, groupPhotos) ->
                                item {
                                    Text(header, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                                groupPhotos.chunked(3).forEach { row ->
                                    item {
                                        val totalAspect = row.sumOf {
                                            val t = thumbnails[it.id]
                                            (if (t != null && t.height > 0) t.width.toFloat() / t.height else if (it.mediaType == VaultMediaType.PDF) 0.75f else 1.33f).toDouble()
                                        }.toFloat()
                                        val gaps = (row.size - 1) * 3f
                                        val rowH = ((folderGridW.value - gaps) / totalAspect).coerceIn(80f, 160f)
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                                            row.forEach { photo ->
                                                val thumb = thumbnails[photo.id]
                                                val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                                Box(Modifier.width((rowH * aspect).dp).height(rowH.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { openViewer(photo) }, contentAlignment = Alignment.Center) {
                                                    if (photo.mediaType == VaultMediaType.PDF) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                            Icon(Icons.Default.PictureAsPdf, stringResource(R.string.cd_pdf_document), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                                            Text(photo.id.let { if (it.length > 15) it.take(12) + "..." else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        }
                                                    } else if (thumb != null) {
                                                        Image(thumb.asImageBitmap(), if (photo.mediaType == VaultMediaType.VIDEO) stringResource(R.string.cd_video_thumbnail) else stringResource(R.string.cd_photo_thumbnail), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                    } else {
                                                        Icon(Icons.Default.Lock, stringResource(R.string.cd_encrypted_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    if (photo.mediaType == VaultMediaType.VIDEO) {
                                                        Icon(Icons.Default.PlayCircleFilled, stringResource(R.string.video), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp).align(Alignment.Center))
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
        }
        } // end else (folder != null)

        VaultPage.TRASH -> {
            Scaffold(topBar = {
                TopAppBar(
                    title = { Text("Trash (${trashItems.size})") },
                    navigationIcon = {
                        IconButton(onClick = { page = VaultPage.CATEGORIES; trashCount = vault.trashCount() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (isSelectionMode && selectedIds.isNotEmpty()) {
                            // Restore selected
                            IconButton(onClick = {
                                val restoredIds = selectedIds.toSet()
                                selectedIds.forEach { vault.restoreFromTrash(it) }
                                trashItems = vault.listTrash()
                                categoryCounts = vault.countByCategory(); trashCount = vault.trashCount()
                                selectedIds = emptySet(); isSelectionMode = false
                                Toast.makeText(context, "Restored", Toast.LENGTH_SHORT).show()
                                // Re-index restored photos in background
                                val pi = photoIndex; val cl = classifier
                                if (pi != null && cl != null) {
                                    scope.launch(Dispatchers.IO) {
                                        val fe = try { FaceEmbedder(context) } catch (_: Exception) { null }
                                        val allPhotos = getAllVaultItems().filter { it.id in restoredIds && it.mediaType == VaultMediaType.PHOTO }
                                        allPhotos.forEach { photo ->
                                            if (!pi.isIndexed(photo.id)) {
                                                val bmp = try {
                                                    if (photo.encryptedFile.length() > 10 * 1024 * 1024) vault.loadThumbnail(photo)
                                                    else vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                                                } catch (_: OutOfMemoryError) { vault.loadThumbnail(photo) }
                                                bmp?.let { img -> try { pi.indexPhoto(photo.id, img, cl, faceEmbedder = fe) } catch (_: Exception) {}; img.recycle() }
                                            }
                                        }
                                        fe?.release()
                                    }
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Restore") }
                            // Permanent delete selected
                            IconButton(onClick = {
                                selectedIds.forEach { vault.permanentDeleteFromTrash(it) }
                                trashItems = vault.listTrash()
                                trashCount = vault.trashCount()
                                selectedIds = emptySet(); isSelectionMode = false
                                Toast.makeText(context, "Permanently deleted", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.Delete, "Delete forever", tint = MaterialTheme.colorScheme.error) }
                            IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, stringResource(R.string.action_cancel)) }
                        } else if (trashItems.isNotEmpty()) {
                            TextButton(onClick = {
                                vault.emptyTrash()
                                trashItems = emptyList()
                                trashThumbnails = emptyMap()
                                trashCount = 0
                                Toast.makeText(context, "Trash emptied", Toast.LENGTH_SHORT).show()
                                page = VaultPage.CATEGORIES
                            }) { Text("Empty Trash", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                )
            }) { padding ->
                if (trashItems.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Delete, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("Trash is empty", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val dateFmt = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        trashItems.forEach { item ->
                            val isSelected = item.id in selectedIds
                            val thumb = trashThumbnails[item.id]
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                            }
                                        },
                                        onLongClick = { isSelectionMode = true; selectedIds = selectedIds + item.id }
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Thumbnail
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    if (thumb != null) {
                                        Image(thumb.asImageBitmap(), "Trashed", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(if (item.mediaType == VaultMediaType.PDF) Icons.Default.PictureAsPdf else Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (item.mediaType == VaultMediaType.VIDEO) {
                                        Icon(Icons.Default.PlayCircleFilled, null, Modifier.size(24.dp), tint = Color.White.copy(alpha = 0.8f))
                                    }
                                }
                                // Info
                                Column(Modifier.weight(1f)) {
                                    Text(item.mediaType.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                                    Text("From: ${item.originalCategory.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Deleted: ${dateFmt.format(java.util.Date(item.trashedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Selection check
                                if (isSelectionMode && isSelected) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Photo editor overlay
    val editPhoto = editorPhoto
    val editBmp = editorBitmap
    if (showEditor && editPhoto != null && editBmp != null) {
        com.privateai.camera.ui.camera.PhotoEditorScreen(
            photo = editPhoto,
            initialBitmap = editBmp,
            vault = vault,
            onDone = {
                showEditor = false
                // Reload the photo to reflect edits
                editorPhoto?.let { photo ->
                    scope.launch {
                        val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(photo) }
                        viewerBitmap = bmp
                    }
                }
                editorPhoto = null
                editorBitmap = null
            }
        )
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
                Text(stringResource(R.string.n_items_count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactCategoryCard(
    label: String, count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Card(Modifier.size(width, 70.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, label, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
