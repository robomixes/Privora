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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.security.SecureNote
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
fun NotesScreen(onBack: (() -> Unit)? = null, filterPersonId: String? = null, openNoteId: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val crypto = remember { CryptoManager(context) }
    val noteRepo = remember { NoteRepository(File(context.filesDir, "vault/notes"), crypto) }

    val startUnlocked = remember {
        VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize()
    }
    var page by remember { mutableStateOf(if (startUnlocked) NotesPage.LIST else NotesPage.LOCKED) }
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }
    var notes by remember { mutableStateOf<List<SecureNote>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingNote by remember { mutableStateOf<SecureNote?>(null) }

    // Deep-link: if openNoteId is provided, jump straight into the editor.
    //   "__new__"  → blank editor (Quick Note widget)
    //   <id>       → open that specific note (AI Assistant ref tap)
    var deepLinkHandled by remember { mutableStateOf(false) }
    if (openNoteId != null && !deepLinkHandled && startUnlocked) {
        if (openNoteId == "__new__") {
            editingNote = null
            page = NotesPage.EDITOR
        } else {
            val target = noteRepo.listNotes().find { it.id == openNoteId }
            if (target != null) {
                editingNote = target
                page = NotesPage.EDITOR
            }
        }
        deepLinkHandled = true
    }

    // Share-to-Privora: if text was shared from another app, create a new note with that content
    var shareHandled by remember { mutableStateOf(false) }
    if (!shareHandled && startUnlocked) {
        val (_, shareText) = com.privateai.camera.MainActivity.consumePendingShare()
        if (!shareText.isNullOrBlank()) {
            // Pre-fill a new note — user lands in the editor to review before saving
            editingNote = SecureNote(
                id = java.util.UUID.randomUUID().toString(),
                title = "",
                content = shareText,
                tags = emptyList(),
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
            page = NotesPage.EDITOR
        }
        shareHandled = true
    }

    // Multi-select
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Auto-lock with shared grace period
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { VaultLockManager.markLeft() }
                Lifecycle.Event.ON_START -> {
                    if (page != NotesPage.LOCKED && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        page = NotesPage.LOCKED; crypto.lock(); VaultLockManager.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refreshNotes() {
        if (isDuressActive) {
            notes = emptyList()
            allTags = emptyList()
            return
        }
        var loadedNotes = when {
            searchQuery.isNotBlank() -> noteRepo.searchNotes(searchQuery)
            selectedTag != null -> noteRepo.listNotes().filter { selectedTag in it.tags }
            else -> noteRepo.listNotes()
        }
        // Filter by personId if navigating from People profile
        if (filterPersonId != null) loadedNotes = loadedNotes.filter { it.personId == filterPersonId }
        notes = loadedNotes
        allTags = noteRepo.getAllTags()
    }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            if (crypto.initialize()) { VaultLockManager.markUnlocked(); refreshNotes(); page = NotesPage.LIST }
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { VaultLockManager.markUnlocked(); refreshNotes(); page = NotesPage.LIST }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.unlock_notes)).setSubtitle(context.getString(R.string.authenticate_to_access_notes))
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
            }, context.getString(R.string.share_notes)
        ))
        selectedIds = emptySet()
        isSelectionMode = false
    }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_n_notes, selectedIds.size)) },
            text = { Text(stringResource(R.string.permanently_deleted)) },
            confirmButton = { TextButton(onClick = { showDeleteDialog = false; deleteSelected() }) { Text(stringResource(R.string.delete), color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Color picker bottom sheet
    if (showColorPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColorPicker = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.choose_color), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
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

    // PIN input state for lock screen
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context)) }

    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(enteredPin: String) {
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, enteredPin)) {
            isDuressActive = true
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked()
            notes = emptyList()
            allTags = emptyList()
            page = NotesPage.LIST
            pinInput = ""
            pinError = null
            scope.launch(Dispatchers.IO) { DuressManager.executeDuress(context, crypto) }
            return
        }

        if (!PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = PinRateLimiter.remainingLockoutMs(context)
            return
        }

        if (com.privateai.camera.security.AppPinManager.verify(context, enteredPin)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) {
                isDuressActive = false
                VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked()
                refreshNotes()
                page = NotesPage.LIST
                pinInput = ""
                pinError = null
            }
            return
        }

        PinRateLimiter.recordFailure(context)
        val remaining = PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) {
            isLockedOut = true
            lockoutRemainingMs = remaining
            pinError = null
        } else {
            pinError = context.getString(R.string.incorrect_pin)
        }
        pinInput = ""
    }

    val currentAuthMode = remember { getAuthMode(context) }

    LaunchedEffect(Unit) {
        if (page == NotesPage.LOCKED) {
            if (currentAuthMode == AuthMode.PHONE_LOCK) {
                authenticate()
            }
        } else {
            refreshNotes()
        }
    }

    when (page) {
        NotesPage.LOCKED -> {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(R.string.secure_notes)) }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                })
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.notes_are_locked), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

                    Spacer(Modifier.height(24.dp))

                    if (currentAuthMode == AuthMode.APP_PIN) {
                        if (isLockedOut) {
                            val seconds = (lockoutRemainingMs / 1000).toInt()
                            Text(
                                stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
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
                                    if (pinError != null) Text(pinError!!, color = MaterialTheme.colorScheme.error)
                                }
                            )

                            Button(
                                onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                                enabled = pinInput.length >= 4,
                                modifier = Modifier.width(200.dp)
                            ) { Text(stringResource(R.string.unlock)) }
                        }
                    } else {
                        Button(
                            onClick = { authenticate() },
                            modifier = Modifier.width(200.dp)
                        ) { Text(stringResource(R.string.unlock)) }
                    }
                }
            }
        }

        NotesPage.LIST -> {
            Scaffold(
                topBar = {
                    if (isSelectionMode) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.n_selected, selectedIds.size)) },
                            navigationIcon = { IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) } },
                            actions = {
                                IconButton(onClick = { pinSelected() }) { Icon(Icons.Default.PushPin, stringResource(R.string.pin)) }
                                IconButton(onClick = { showColorPicker = true }) { Icon(Icons.Default.Palette, stringResource(R.string.color)) }
                                IconButton(onClick = { shareSelected() }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                                IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                        )
                    } else {
                        TopAppBar(title = { Text(stringResource(R.string.secure_notes_count, notes.size)) }, navigationIcon = {
                            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        })
                    }
                },
                floatingActionButton = {
                    if (!isSelectionMode) {
                        FloatingActionButton(onClick = { editingNote = null; page = NotesPage.EDITOR }) {
                            Icon(Icons.Default.Add, stringResource(R.string.new_note))
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (!isSelectionMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; refreshNotes() },
                            placeholder = { Text(stringResource(R.string.search_notes)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (allTags.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(selected = selectedTag == null, onClick = { selectedTag = null; refreshNotes() }, label = { Text(stringResource(R.string.all)) })
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
                            Text(stringResource(R.string.no_notes_yet), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                            Text(stringResource(R.string.tap_plus_to_create), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onSave = { title, content, tags, attachments, audioAttachments, personId ->
                    if (editingNote != null) {
                        noteRepo.saveNote(editingNote!!.copy(title = title, content = content, tags = tags, attachments = attachments, audioAttachments = audioAttachments, personId = personId))
                    } else {
                        noteRepo.createNote(title, content, tags, attachments, audioAttachments, personId)
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

                // Indicators row: attachments, voice, person
                val hasAttachments = note.attachments.isNotEmpty()
                val hasAudio = note.audioAttachments.isNotEmpty()
                val hasPerson = note.personId != null
                if (hasAttachments || hasAudio || hasPerson) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (hasAttachments) Text("\uD83D\uDCCE ${note.attachments.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (hasAudio) Text("\uD83C\uDFA4 ${note.audioAttachments.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (hasPerson) Text("\uD83D\uDC64", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
