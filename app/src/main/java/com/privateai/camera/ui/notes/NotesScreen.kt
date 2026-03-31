package com.privateai.camera.ui.notes

import android.content.Intent
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.SecureNote
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val NOTE_COLORS = listOf(
    Color.Transparent,           // 0 = default
    Color(0xFFFFF9C4),           // 1 = yellow
    Color(0xFFDCEDC8),           // 2 = green
    Color(0xFFB3E5FC),           // 3 = blue
    Color(0xFFF8BBD0),           // 4 = pink
    Color(0xFFE1BEE7),           // 5 = purple
    Color(0xFFFFCCBC),           // 6 = orange
    Color(0xFFCFD8DC),           // 7 = gray
    Color(0xFFFFE0B2),           // 8 = amber
)

private enum class NotesPage { LOCKED, LIST, EDITOR }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val crypto = remember { CryptoManager(context) }
    val noteRepo = remember { NoteRepository(File(context.filesDir, "vault/notes"), crypto) }

    var page by remember { mutableStateOf(NotesPage.LOCKED) }
    var notes by remember { mutableStateOf<List<SecureNote>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingNote by remember { mutableStateOf<SecureNote?>(null) }

    // Multi-select
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { page = NotesPage.LOCKED; crypto.lock() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refreshNotes() {
        notes = when {
            searchQuery.isNotBlank() -> noteRepo.searchNotes(searchQuery)
            selectedTag != null -> noteRepo.listNotes().filter { selectedTag in it.tags }
            else -> noteRepo.listNotes()
        }
        allTags = noteRepo.getAllTags()
    }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            if (crypto.initialize()) { refreshNotes(); page = NotesPage.LIST }
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { refreshNotes(); page = NotesPage.LIST }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Notes").setSubtitle("Authenticate to access secure notes")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build())
    }

    fun deleteSelected() {
        selectedIds.forEach { noteRepo.deleteNote(it) }
        selectedIds = emptySet()
        isSelectionMode = false
        refreshNotes()
    }

    fun pinSelected() {
        val toPin = notes.filter { it.id in selectedIds }
        val shouldPin = toPin.any { !it.pinned }
        toPin.forEach { noteRepo.saveNote(it.copy(pinned = shouldPin)) }
        selectedIds = emptySet()
        isSelectionMode = false
        refreshNotes()
    }

    fun setColorSelected(color: Int) {
        notes.filter { it.id in selectedIds }.forEach { noteRepo.saveNote(it.copy(color = color)) }
        selectedIds = emptySet()
        isSelectionMode = false
        showColorPicker = false
        refreshNotes()
    }

    fun shareSelected() {
        val toShare = notes.filter { it.id in selectedIds }
        val text = toShare.joinToString("\n\n---\n\n") { note ->
            buildString {
                if (note.title.isNotBlank()) appendLine(note.title)
                append(note.content)
            }
        }
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share notes"
        ))
        selectedIds = emptySet()
        isSelectionMode = false
    }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selectedIds.size} note(s)") },
            text = { Text("Permanently deleted.") },
            confirmButton = { TextButton(onClick = { showDeleteDialog = false; deleteSelected() }) { Text("Delete", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    // Color picker bottom sheet
    if (showColorPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColorPicker = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Choose color", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NOTE_COLORS.forEachIndexed { index, color ->
                        val displayColor = if (color == Color.Transparent) MaterialTheme.colorScheme.surface else color
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(displayColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { setColorSelected(index) }
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    LaunchedEffect(Unit) { if (page == NotesPage.LOCKED) authenticate() }

    when (page) {
        NotesPage.LOCKED -> {
            Scaffold(topBar = {
                TopAppBar(title = { Text("Secure Notes") }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                })
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Notes are locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                    TextButton(onClick = { authenticate() }, Modifier.padding(top = 24.dp)) { Text("Unlock") }
                }
            }
        }

        NotesPage.LIST -> {
            Scaffold(
                topBar = {
                    if (isSelectionMode) {
                        TopAppBar(
                            title = { Text("${selectedIds.size} selected") },
                            navigationIcon = { IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, "Cancel") } },
                            actions = {
                                IconButton(onClick = { pinSelected() }) { Icon(Icons.Default.PushPin, "Pin") }
                                IconButton(onClick = { showColorPicker = true }) { Icon(Icons.Default.Palette, "Color") }
                                IconButton(onClick = { shareSelected() }) { Icon(Icons.Default.Share, "Share") }
                                IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        )
                    } else {
                        TopAppBar(title = { Text("Secure Notes (${notes.size})") }, navigationIcon = {
                            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        })
                    }
                },
                floatingActionButton = {
                    if (!isSelectionMode) {
                        FloatingActionButton(onClick = { editingNote = null; page = NotesPage.EDITOR }) {
                            Icon(Icons.Default.Add, "New Note")
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (!isSelectionMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; refreshNotes() },
                            placeholder = { Text("Search notes...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (allTags.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(selected = selectedTag == null, onClick = { selectedTag = null; refreshNotes() }, label = { Text("All") })
                                allTags.take(5).forEach { tag ->
                                    FilterChip(
                                        selected = selectedTag == tag,
                                        onClick = { selectedTag = if (selectedTag == tag) null else tag; refreshNotes() },
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }
                    }

                    if (notes.isEmpty()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.NoteAlt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No notes yet", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                            Text("Tap + to create one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notes) { note ->
                                val isSelected = note.id in selectedIds
                                NoteCard(
                                    note = note,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - note.id else selectedIds + note.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            editingNote = note
                                            page = NotesPage.EDITOR
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        selectedIds = setOf(note.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        NotesPage.EDITOR -> {
            NoteEditorScreen(
                note = editingNote,
                allTags = allTags,
                onSave = { title, content, tags ->
                    if (editingNote != null) {
                        noteRepo.saveNote(editingNote!!.copy(title = title, content = content, tags = tags))
                    } else {
                        noteRepo.createNote(title, content, tags)
                    }
                    refreshNotes()
                    page = NotesPage.LIST
                },
                onDelete = {
                    editingNote?.let { noteRepo.deleteNote(it.id) }
                    refreshNotes()
                    page = NotesPage.LIST
                },
                onBack = { page = NotesPage.LIST }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: SecureNote,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val bgColor = NOTE_COLORS.getOrElse(note.color) { Color.Transparent }
    val cardColor = if (bgColor == Color.Transparent) MaterialTheme.colorScheme.surface else bgColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Box {
            Column(Modifier.padding(12.dp)) {
                // Pin indicator
                if (note.pinned) {
                    Icon(
                        Icons.Default.PushPin, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (note.title.isNotBlank()) {
                    Text(note.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                }

                if (note.content.isNotBlank()) {
                    Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                }

                if (note.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        note.tags.forEach { tag ->
                            Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Text(dateFormat.format(Date(note.modifiedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Selection checkmark
            if (isSelectionMode && isSelected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
