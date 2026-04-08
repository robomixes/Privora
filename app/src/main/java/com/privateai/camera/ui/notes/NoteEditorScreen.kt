package com.privateai.camera.ui.notes

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.SecureNote
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    note: SecureNote?,
    allTags: List<String>,
    onSave: (title: String, content: String, tags: List<String>, attachments: List<String>, personId: String?) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isNew = note == null
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var tags by remember { mutableStateOf(note?.tags ?: emptyList()) }
    var newTag by remember { mutableStateOf("") }
    var showTagInput by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var attachmentIds by remember { mutableStateOf(note?.attachments ?: emptyList()) }
    var attachmentThumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var showVaultPicker by remember { mutableStateOf(false) }
    var linkedPersonId by remember { mutableStateOf(note?.personId) }
    var showPersonPicker by remember { mutableStateOf(false) }
    var linkedPersonName by remember { mutableStateOf<String?>(null) }

    // Load linked person name
    LaunchedEffect(linkedPersonId) {
        if (linkedPersonId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val crypto = CryptoManager(context).also { it.initialize() }
                    val contactRepo = com.privateai.camera.security.ContactRepository(java.io.File(context.filesDir, "vault/contacts"), crypto, com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto))
                    linkedPersonName = contactRepo.listContacts().find { it.id == linkedPersonId }?.name
                } catch (_: Exception) {}
            }
        } else linkedPersonName = null
    }

    // Load thumbnails for existing attachments (includes folder items)
    LaunchedEffect(attachmentIds) {
        val crypto = CryptoManager(context).also { it.initialize() }
        val vault = VaultRepository(context, crypto)
        val folderManager = com.privateai.camera.security.FolderManager(context, crypto)
        val thumbs = mutableMapOf<String, Bitmap>()
        withContext(Dispatchers.IO) {
            val folderItems = folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
            val allPhotos = (vault.listAllPhotos() + folderItems).distinctBy { it.id }
            attachmentIds.forEach { id ->
                allPhotos.find { it.id == id }?.let { photo ->
                    vault.loadThumbnail(photo)?.let { thumbs[id] = it }
                }
            }
        }
        attachmentThumbnails = thumbs
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Vault photo picker dialog
    if (showVaultPicker) {
        VaultPhotoPickerDialog(
            alreadyAttached = attachmentIds,
            onConfirm = { selectedIds ->
                attachmentIds = (attachmentIds + selectedIds).distinct()
                showVaultPicker = false
            },
            onDismiss = { showVaultPicker = false }
        )
    }

    val brandColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = { showVaultPicker = true }) {
                        Icon(Icons.Default.AttachFile, stringResource(R.string.attach_from_vault), tint = brandColor)
                    }
                    IconButton(
                        onClick = { onSave(title, content, tags, attachmentIds, linkedPersonId) },
                        enabled = title.isNotBlank() || content.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.save), tint = if (title.isNotBlank() || content.isNotBlank()) brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title — borderless, large, bold
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                cursorBrush = SolidColor(brandColor),
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(stringResource(R.string.title_label), style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    innerTextField()
                }
            )

            // Tags — compact chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tags.forEach { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(brandColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .clickable { tags = tags - tag }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("$tag  ✕", style = MaterialTheme.typography.labelSmall, color = brandColor)
                    }
                }
                allTags.filter { it !in tags }.take(3).forEach { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .clickable { tags = tags + tag }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showTagInput) {
                    BasicTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        modifier = Modifier.width(80.dp)
                            .border(1.dp, brandColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(brandColor),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    IconButton(onClick = {
                        if (newTag.isNotBlank()) { tags = tags + newTag.trim(); newTag = "" }
                        showTagInput = false
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Check, stringResource(R.string.add_tag), Modifier.size(16.dp), tint = brandColor)
                    }
                } else {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                            .clickable { showTagInput = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.new_tag), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Attached photos — rounded thumbnails with proper delete overlay
            if (attachmentIds.isNotEmpty() || true) { // Always show row to allow adding
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachmentIds.forEach { id ->
                        Box(modifier = Modifier.size(72.dp)) {
                            val thumb = attachmentThumbnails[id]
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Image, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // Delete overlay — semi-transparent circle
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { attachmentIds = attachmentIds - id },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, stringResource(R.string.remove_attachment), Modifier.size(12.dp), tint = Color.White)
                            }
                        }
                    }
                    // Add photo button
                    Box(
                        Modifier.size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(brandColor.copy(alpha = 0.08f))
                            .border(1.5.dp, brandColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { showVaultPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, null, Modifier.size(20.dp), tint = brandColor)
                            Text("Photo", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = brandColor)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Person link — optional, tap to pick from People
            if (linkedPersonName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, null, Modifier.size(16.dp), tint = brandColor)
                    Text(linkedPersonName!!, style = MaterialTheme.typography.bodySmall, color = brandColor)
                    IconButton(onClick = { linkedPersonId = null; linkedPersonName = null }, Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, "Unlink", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Row(Modifier.clickable { showPersonPicker = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text("Link to person", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }

            if (showPersonPicker) {
                val contacts = remember {
                    try {
                        val crypto = CryptoManager(context).also { it.initialize() }
                        com.privateai.camera.security.ContactRepository(java.io.File(context.filesDir, "vault/contacts"), crypto, com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)).listContacts()
                    } catch (_: Exception) { emptyList() }
                }
                AlertDialog(
                    onDismissRequest = { showPersonPicker = false },
                    title = { Text("Link to Person") },
                    text = {
                        if (contacts.isEmpty()) {
                            Text("No people added yet. Add someone in People first.")
                        } else {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                contacts.forEach { person ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .clickable { linkedPersonId = person.id; linkedPersonName = person.name; showPersonPicker = false }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(Modifier.size(32.dp).background(brandColor, CircleShape), contentAlignment = Alignment.Center) {
                                            Text(person.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Text(person.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showPersonPicker = false }) { Text("Cancel") } }
                )
            }

            Spacer(Modifier.height(4.dp))

            // Content — borderless, natural text area
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                cursorBrush = SolidColor(brandColor),
                decorationBox = { innerTextField ->
                    if (content.isEmpty()) {
                        Text(stringResource(R.string.note_label), style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun VaultPhotoPickerDialog(
    alreadyAttached: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var vaultPhotos by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load vault photos (only images, not videos/PDFs)
    LaunchedEffect(Unit) {
        val crypto = CryptoManager(context).also { it.initialize() }
        val vault = VaultRepository(context, crypto)
        withContext(Dispatchers.IO) {
            val folderManager = com.privateai.camera.security.FolderManager(context, crypto)
            val folderItems = folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
            val photos = (vault.listAllPhotos() + folderItems).distinctBy { it.id }.filter { it.mediaType == VaultMediaType.PHOTO }.sortedByDescending { it.timestamp }
            val thumbs = mutableMapOf<String, Bitmap>()
            photos.forEach { photo ->
                vault.loadThumbnail(photo)?.let { thumbs[photo.id] = it }
            }
            vaultPhotos = photos
            thumbnails = thumbs
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_from_vault)) },
        text = {
            if (isLoading) {
                Text(stringResource(R.string.loading))
            } else if (vaultPhotos.isEmpty()) {
                Text(stringResource(R.string.no_vault_photos))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(vaultPhotos) { photo ->
                        val isSelected = photo.id in selectedIds
                        val isAlreadyAttached = photo.id in alreadyAttached
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else if (isAlreadyAttached) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = !isAlreadyAttached) {
                                    selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                }
                        ) {
                            val thumb = thumbnails[photo.id]
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text(stringResource(R.string.attach_count, selectedIds.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
