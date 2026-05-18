// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.assistant

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.bridge.AssistantPrompts
import com.privateai.camera.bridge.AssistantTools
import com.privateai.camera.bridge.GemmaRunner
import com.privateai.camera.bridge.KnowledgeSnapshot
import com.privateai.camera.bridge.ParsedReply
import com.privateai.camera.bridge.ProposedAction
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AssistantScreen"

/**
 * Top-level AI Assistant chat surface.
 *
 * - Entry: ✨ icon in Home top bar (Grid + Tabs layouts).
 * - Chat history: in-memory only, clears when user navigates away.
 * - Per-turn: builds a fresh [KnowledgeSnapshot], sends it + user message
 *   to Gemma, handles optional tool calls (search_notes), renders answer.
 */
/**
 * Singleton session holder — survives navigation away + back.
 * Cleared only when the app process dies (privacy: nothing on disk).
 */
private object AssistantSession {
    val messages = mutableStateListOf<ChatMessage>()
    /**
     * The vault document the user is currently asking about, if any.
     * Set when the user enters the assistant via the vault PDF viewer's
     * "Summarize" / "Ask the Assistant" entries. Bound to the chat session
     * so follow-up questions ("now translate this", "give me the next part")
     * stay grounded without re-specifying the doc id.
     *
     * The id is the encrypted-file basename (e.g. `scan_1714944000000.pdf`).
     * Using the id directly in tool calls fails on Gemma 4 E2B — the model
     * truncates the 13-digit timestamp. We bypass that by injecting the OCR
     * text into the prompt directly (see `runAssistantTurn`).
     *
     * Backed by a Compose `mutableStateOf` so the attached-doc chip can
     * react when the user taps × to detach.
     */
    private val _attachedDocId = mutableStateOf<String?>(null)
    var attachedDocId: String?
        get() = _attachedDocId.value
        set(value) { _attachedDocId.value = value }

    /**
     * Image the user has attached for the next message via the input-row
     * paperclip button. One image at a time. Held as a content URI; the
     * send path decrypts/copies it to a temp file before handing to
     * [com.privateai.camera.bridge.GemmaRunner.describeImage].
     *
     * Cleared after a successful send so the next turn defaults back to
     * pure-text mode. The user can also tap × on the preview chip to
     * detach without sending.
     */
    private val _attachedImageUri = mutableStateOf<android.net.Uri?>(null)
    var attachedImageUri: android.net.Uri?
        get() = _attachedImageUri.value
        set(value) { _attachedImageUri.value = value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: (() -> Unit)? = null,
    onNavigate: ((route: String) -> Unit)? = null,
    seedPrompt: String? = null,
    attachedDocId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = AssistantSession.messages
    var thinking by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var showLanguagePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Pre-warm the Gemma engine so the first reply doesn't pay cold-load latency
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { GemmaRunner.load(context) }
    }

    // Photo-picker launcher for the image-attach (paperclip) button in the
    // input row. Uses the modern PickVisualMedia contract (no storage
    // permission needed; system picker isolates the gallery). The picked
    // URI is stashed on AssistantSession so the preview chip + send path
    // can read it. One image at a time; picking again replaces.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) AssistantSession.attachedImageUri = uri
    }
    // Read the attached image reactively so the preview chip + send-button
    // enabled state recompose when the user attaches or clears.
    val attachedImage: android.net.Uri? = AssistantSession.attachedImageUri
    var showAttachMenu by remember { mutableStateOf(false) }
    var showVaultPicker by remember { mutableStateOf(false) }
    // Decoded thumbnail for the preview chip — cached in memory while the
    // URI is attached. Decoded once via ContentResolver.openInputStream.
    val attachedImageThumb = remember(attachedImage) {
        if (attachedImage == null) null
        else try {
            context.contentResolver.openInputStream(attachedImage)?.use { ins ->
                android.graphics.BitmapFactory.decodeStream(ins)
            }
        } catch (e: Exception) { Log.w(TAG, "thumb decode failed: ${e.message}"); null }
    }

    // Deep-link: bind the doc id to the session SYNCHRONOUSLY during
    // composition (via remember keyed on attachedDocId) so the attached-doc
    // chip renders on the first frame, not after a LaunchedEffect fires.
    // The session backing is a Compose mutableStateOf, so writes are
    // observed by the chip's reader and trigger recomposition cleanly.
    // Doc-switch behaviour: wipe the chat when moving between different
    // docs so prior summaries don't bias the next one; first attach (prior
    // session state is null) preserves any unrelated chat.
    remember(attachedDocId) {
        if (!attachedDocId.isNullOrBlank()) {
            val previous = AssistantSession.attachedDocId
            if (previous != null && previous != attachedDocId) {
                AssistantSession.messages.clear()
            }
            AssistantSession.attachedDocId = attachedDocId
        }
        Unit
    }

    // Seed prompt fill — runs once per new attachedDocId. The seed is a
    // UI-state side effect (writes to inputText), so it lives in
    // LaunchedEffect rather than the synchronous remember block above.
    var seedConsumed by remember(attachedDocId) { mutableStateOf(false) }
    LaunchedEffect(seedPrompt, attachedDocId) {
        if (!seedConsumed && !seedPrompt.isNullOrBlank()) {
            inputText = seedPrompt
            seedConsumed = true
        }
    }

    // Auto-scroll to bottom when a new message is added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        val pendingImage = AssistantSession.attachedImageUri
        // An attached image counts as content too — accept image-only sends
        // (defaults to "Describe this image").
        if (text.isEmpty() && pendingImage == null) return
        if (thinking) return
        inputText = ""
        // Image-attached send: bypass the regular runAssistantTurn pipeline
        // (snapshot + tools + multi-turn) and call describeImage directly.
        // Single-shot vision pass; the user's typed text becomes the prompt
        // (or a localized "Describe this image" default).
        if (pendingImage != null) {
            // Capture the user's bitmap once so the chat bubble can render
            // it even after the URI is detached. PickVisualMedia's grant
            // may not survive past the send.
            val capturedBitmap = attachedImageThumb
            AssistantSession.attachedImageUri = null  // detach now so chip clears + repeated sends require re-pick
            val displayText = text.ifBlank { context.getString(R.string.assistant_image_default_prompt) }
            messages += ChatMessage.User(displayText, image = capturedBitmap)
            thinking = true
            scope.launch {
                val reply = withContext(Dispatchers.IO) {
                    runImageQueryTurn(context, pendingImage, displayText)
                }
                messages += ChatMessage.Assistant(reply ?: context.getString(R.string.assistant_error_generic), emptyList())
                thinking = false
            }
            return
        }
        if (text.isEmpty()) return
        messages += ChatMessage.User(text)
        thinking = true

        // The trailing function body lives below — keep this declaration as the
        // single source of truth for the message-send flow; quick-action chips
        // route through here by prepending a directive and calling sendMessage().

        // Streaming index is allocated lazily — set when the first non-empty
        // chunk arrives, so the typing indicator is replaced cleanly by a real
        // bubble (no empty placeholder rendered alongside it).
        var streamIndex = -1

        scope.launch {
            try {
                val historySnapshot = messages.toList()
                val turn = withContext(Dispatchers.IO) {
                    runAssistantTurn(context, historySnapshot, text) { partial ->
                        if (partial.isEmpty()) return@runAssistantTurn
                        // Tokens arrive on IO. Hop to main to mutate Compose state.
                        scope.launch {
                            if (streamIndex < 0) {
                                streamIndex = messages.size
                                messages += ChatMessage.Assistant(partial, emptyList())
                                thinking = false
                            } else if (streamIndex < messages.size) {
                                messages[streamIndex] = ChatMessage.Assistant(partial, emptyList())
                            }
                        }
                    }
                }
                // Final pass: replace the streaming bubble with the cleaned
                // answer + any proposed action (or append a fresh one if nothing
                // streamed — e.g. the model emitted only an action JSON we held back).
                val finalMsg = ChatMessage.Assistant(turn.text, turn.refs, turn.action, photoThumbs = turn.photoThumbs)
                if (streamIndex < 0) {
                    messages += finalMsg
                } else if (streamIndex < messages.size) {
                    messages[streamIndex] = finalMsg
                }
            } catch (e: Exception) {
                Log.e(TAG, "Assistant error: ${e.message}", e)
                val errMsg = ChatMessage.Assistant(context.getString(R.string.assistant_error_generic), emptyList())
                if (streamIndex < 0) messages += errMsg
                else if (streamIndex < messages.size) messages[streamIndex] = errMsg
            } finally {
                thinking = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.assistant_title))
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // "New chat" — clears the session so the user starts fresh
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = {
                            messages.clear()
                            // Drop the doc binding when starting a fresh chat — the
                            // user shouldn't keep accidentally answering against the
                            // last doc they were asking about.
                            AssistantSession.attachedDocId = null
                        }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.assistant_new_chat),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Attached-document chip — always visible while a vault doc is
            // bound to the session, so the user can see which doc the
            // assistant is grounded in (not just in the first chat bubble).
            // Tap × to detach: clears the session binding so the next message
            // is a normal chat, not a doc Q&A. The chat history is preserved.
            val currentAttachedDoc = AssistantSession.attachedDocId
            if (!currentAttachedDoc.isNullOrBlank()) {
                val displayName = currentAttachedDoc.removeSuffix(".pdf").removeSuffix(".PDF")
                androidx.compose.material3.AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(R.string.assistant_attached_doc, displayName),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Description,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { AssistantSession.attachedDocId = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.assistant_attached_doc_detach),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                // OCR-language disclaimer. ML Kit Text Recognition v2 (Latin
                // only) silently drops Arabic / Hebrew / CJK / Thai etc., so
                // for mixed-script docs the OCR text is incomplete — and
                // Gemma can't tell because there are no gap markers to look
                // for. Surfacing the limitation to the user directly is the
                // honest move until non-Latin OCR (Tesseract or equivalent)
                // is added.
                Text(
                    stringResource(R.string.assistant_attached_doc_latin_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            // Chat messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = listState
            ) {
                // Empty state with example chips
                if (messages.isEmpty() && !thinking) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                stringResource(R.string.assistant_empty_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.assistant_try_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val examples = listOf(
                                stringResource(R.string.assistant_example_week),
                                stringResource(R.string.assistant_example_search),
                                stringResource(R.string.assistant_example_summarize),
                                stringResource(R.string.assistant_example_habits)
                            )
                            examples.forEach { example ->
                                AssistChip(
                                    onClick = { inputText = example },
                                    label = { Text(example, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }

                itemsIndexed(messages) { msgIndex, msg ->
                    ChatBubble(
                        message = msg,
                        onRefClick = { ref ->
                            val route = when (ref.kind) {
                                RefKind.NOTE -> "notes?openNoteId=${ref.id}"
                                RefKind.REMINDER -> "reminders"
                                RefKind.HABIT -> "insights?tab=habits"
                                RefKind.HEALTH -> "health"
                            }
                            onNavigate?.invoke(route)
                        },
                        onActionConfirm = { action ->
                            val current = messages.getOrNull(msgIndex) as? ChatMessage.Assistant ?: return@ChatBubble
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    com.privateai.camera.bridge.AssistantActions.execute(context, action)
                                }
                                val updated = current.copy(
                                    actionStatus = if (ok) ActionStatus.ADDED else ActionStatus.FAILED
                                )
                                if (msgIndex < messages.size) messages[msgIndex] = updated
                            }
                        },
                        onActionDismiss = {
                            val current = messages.getOrNull(msgIndex) as? ChatMessage.Assistant ?: return@ChatBubble
                            messages[msgIndex] = current.copy(actionStatus = ActionStatus.DISMISSED)
                        },
                        onSaveAsNote = { replyText ->
                            // Guard against re-taps before the save completes.
                            // The button itself disables when savedAsNote becomes
                            // true, but this catches concurrent taps too.
                            val cur = messages.getOrNull(msgIndex) as? ChatMessage.Assistant
                            if (cur == null || cur.savedAsNote) return@ChatBubble
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    try {
                                        val crypto = CryptoManager(context).also { it.initialize() }
                                        val noteRepo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)
                                        val title = replyText.lineSequence().firstOrNull { it.isNotBlank() }
                                            ?.take(60)?.trim()
                                            ?: context.getString(R.string.assistant_saved_default_title)
                                        noteRepo.createNote(
                                            title = title,
                                            content = replyText,
                                            tags = listOf("assistant")
                                        )
                                        true
                                    } catch (_: Exception) { false }
                                }
                                if (ok && msgIndex < messages.size) {
                                    val updated = (messages[msgIndex] as? ChatMessage.Assistant)?.copy(savedAsNote = true)
                                    if (updated != null) messages[msgIndex] = updated
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.assistant_saved_to_notes
                                        else R.string.note_save_failed
                                    ),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onPhotoClick = { photoId ->
                            // search_photos tap → deep-link the Vault to
                            // auto-open this photo in the viewer (the
                            // openPhotoId route runs openViewer post-unlock).
                            onNavigate?.invoke("vault?openPhotoId=$photoId")
                        }
                    )
                }

                if (thinking) {
                    item { ThinkingBubble() }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }

            // Attached image preview — sits between the chat list and the
            // input row when the user has picked an image for the next
            // message. Tap × to detach without sending.
            if (attachedImage != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (attachedImageThumb != null) {
                        Image(
                            bitmap = attachedImageThumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Image, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(R.string.assistant_attached_image),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { AssistantSession.attachedImageUri = null }) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.assistant_attached_image_detach)
                        )
                    }
                }
            }

            // Input bar — ChatGPT-style pill: a single rounded Surface that
            // wraps a leading "+" attach button, an expanding text field
            // (1 line at rest, grows up to 6 lines), and a trailing send
            // circle. No more 110dp tall block by default — the input only
            // takes the height the user's text needs.
            val canSend = (inputText.isNotBlank() || attachedImage != null) && !thinking
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Leading "+" attach button → dropdown (Device gallery /
                    // Privora vault). Same enable / disable logic as before.
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            enabled = !thinking
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.assistant_attach_image),
                                tint = if (!thinking) MaterialTheme.colorScheme.onSurfaceVariant
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assistant_attach_from_gallery)) },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                                onClick = {
                                    showAttachMenu = false
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assistant_attach_from_vault)) },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                onClick = {
                                    showAttachMenu = false
                                    showVaultPicker = true
                                }
                            )
                        }
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        enabled = !thinking,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        decorationBox = { inner ->
                            if (inputText.isEmpty()) {
                                Text(
                                    stringResource(R.string.assistant_input_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            inner()
                        }
                    )
                    // Trailing send: filled circle when enabled, neutral when not.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(enabled = canSend) { sendMessage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.assistant_send),
                            modifier = Modifier.size(18.dp),
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            // Char counter — surfaces only when the user has typed enough that
            // length matters (e.g., targeting an email of a specific size).
            if (inputText.length > 50) {
                Text(
                    stringResource(R.string.assistant_chars, inputText.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Quick-action tag row — under the input field. Each chip sends
            // a writing-task directive. Works when there's typed text OR an
            // attached image / PDF (so the user can attach a doc and tap
            // "Summarize" without typing anything). With no text, the
            // directive becomes the prompt verbatim.
            val canApplyChip = (inputText.isNotBlank() ||
                attachedImage != null ||
                AssistantSession.attachedDocId != null) && !thinking
            fun applyQuickAction(directive: String) {
                if (!canApplyChip) return
                val typed = inputText.trim()
                inputText = if (typed.isNotEmpty()) "$directive\n\n$typed" else directive
                sendMessage()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_grammar),
                    icon = Icons.Default.Spellcheck,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_grammar))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_rewrite),
                    icon = Icons.Default.AutoFixHigh,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_rewrite))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_summarize),
                    icon = Icons.AutoMirrored.Filled.ShortText,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_summarize))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_formal),
                    icon = Icons.Default.WorkOutline,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_formal))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_simple),
                    icon = Icons.Default.Lightbulb,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_simple))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_translate),
                    icon = Icons.Default.Translate,
                    enabled = canApplyChip,
                    onClick = {
                        // Defer the directive until the user picks a target language.
                        showLanguagePicker = true
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_expand),
                    icon = Icons.Default.UnfoldMore,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_expand))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_bullets),
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_bullets))
                    }
                )
            }

            // Translate target-language picker — opened from the Translate chip.
            // Five built-in languages match Privora's UI locales; users can extend
            // by typing a language name themselves into the chat.
            if (showLanguagePicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLanguagePicker = false },
                    title = { Text(stringResource(R.string.assistant_translate_pick_title)) },
                    text = {
                        Column {
                            val languages = listOf(
                                R.string.assistant_translate_lang_en to "English",
                                R.string.assistant_translate_lang_fr to "French",
                                R.string.assistant_translate_lang_es to "Spanish",
                                R.string.assistant_translate_lang_zh to "Chinese",
                                R.string.assistant_translate_lang_ar to "Arabic"
                            )
                            languages.forEach { (labelRes, modelLangName) ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showLanguagePicker = false
                                        applyQuickAction(
                                            context.getString(R.string.assistant_directive_translate, modelLangName)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(labelRes), modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showLanguagePicker = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            // Vault-content picker — bottom sheet listing recent vault images
            // AND PDFs. Image → decrypt to temp JPEG → set as attached image.
            // PDF → set attachedDocId so the existing OCR-injection path
            // grounds the next assistant turn in the doc text.
            if (showVaultPicker) {
                VaultPhotoPickerSheet(
                    onPicked = { tempUri ->
                        AssistantSession.attachedImageUri = tempUri
                        showVaultPicker = false
                    },
                    onPdfPicked = { pdfId ->
                        // Switching docs wipes prior chat so a previous
                        // summary doesn't bias the next answer (matches the
                        // existing PDF-viewer → Assistant deep-link behavior).
                        val previous = AssistantSession.attachedDocId
                        if (previous != null && previous != pdfId) {
                            AssistantSession.messages.clear()
                        }
                        AssistantSession.attachedDocId = pdfId
                        showVaultPicker = false
                    },
                    onDismiss = { showVaultPicker = false }
                )
            }
        }
    }
}

/** Result of one assistant turn — final cleaned text + any data refs + an optional proposed action + optional photo thumbs. */
private data class TurnResult(
    val text: String,
    val refs: List<DataRef>,
    val action: ProposedAction? = null,
    val photoThumbs: List<PhotoThumb> = emptyList()
)

/**
 * Build the chat-bubble summary for a search_photos tool result host-side.
 * Bypassing the LLM here avoids Gemma's number-hallucination tendency on
 * digits inside structured prompts (logged a "total:87" → "found 7 photos"
 * mis-copy). The thumb strip already shows the visual result; the text
 * just needs to confirm the count + what was matched.
 */
/**
 * One-shot vision query: decrypt/copy the picked image URI into a temp JPEG
 * and hand it to [GemmaRunner.describeImage] with the user's typed prompt.
 *
 * Bypasses [runAssistantTurn] because the regular flow does tool-calling
 * + knowledge-snapshot context which adds zero value when the user is
 * asking about a specific photo they just attached.
 */
private suspend fun runImageQueryTurn(
    context: android.content.Context,
    imageUri: android.net.Uri,
    userPrompt: String
): String? {
    if (!GemmaRunner.isAvailable(context)) return null
    val tempFile = File(context.cacheDir, "assistant_img_${System.currentTimeMillis()}.jpg")
    try {
        // Copy the picker URI to a real file path. PickVisualMedia hands us
        // a content:// URI which GemmaRunner.describeImage can't open
        // directly (it takes a filesystem path).
        context.contentResolver.openInputStream(imageUri)?.use { ins ->
            tempFile.outputStream().use { out -> ins.copyTo(out) }
        }
        if (!tempFile.exists() || tempFile.length() == 0L) {
            Log.w(TAG, "runImageQueryTurn: temp file empty / missing")
            return null
        }
        // Honor user's UI language for the reply.
        val prompt = com.privateai.camera.bridge.GemmaPrompts.askAboutImage(userPrompt)
        return GemmaRunner.describeImage(context, tempFile.absolutePath, prompt)?.trim()
    } catch (e: Exception) {
        Log.w(TAG, "runImageQueryTurn failed: ${e.message}")
        return null
    } finally {
        try { tempFile.delete() } catch (_: Exception) {}
    }
}

private fun buildSearchPhotosSummary(
    context: android.content.Context,
    leanJson: String,
    originalQuery: String,
    thumbsShown: Int
): String {
    return try {
        val obj = org.json.JSONObject(leanJson)
        val total = obj.optInt("total", 0)
        val person = obj.optString("detectedPerson", "").ifBlank { null }
        val residual = obj.optString("residualQuery", "").trim()
        val date = obj.optString("detectedDate", "").ifBlank { null }

        if (total == 0) {
            context.getString(R.string.assistant_search_photos_none, originalQuery)
        } else {
            // Compose a single sentence. Single-form strings (no plurals)
            // keep the locale work simple and avoid mistakes — the digit
            // carries the count info; grammar slack on "1 photo" vs "1
            // photos" is acceptable for an in-chat status line.
            val parts = mutableListOf<String>()
            if (person != null) parts.add(context.getString(R.string.assistant_search_photos_of, person))
            if (residual.isNotBlank()) parts.add(context.getString(R.string.assistant_search_photos_with, residual))
            if (date != null) parts.add(context.getString(R.string.assistant_search_photos_from, date))

            val suffix = if (parts.isEmpty()) "" else " " + parts.joinToString(" ")
            val head = context.getString(R.string.assistant_search_photos_found, total)
            val tail = if (thumbsShown in 1 until total) " " + context.getString(R.string.assistant_search_photos_showing, thumbsShown) else ""
            "$head$suffix.$tail"
        }
    } catch (_: Exception) {
        // Defensive fallback — should never trigger given the lean JSON is built host-side
        context.getString(R.string.assistant_search_photos_generic)
    }
}

/**
 * Build the chat-bubble summary for a summarize_expenses tool result.
 * Same anti-hallucination rationale as the search_photos helper: Gemma
 * mis-copies digits from structured JSON ("total":"88.00" → wrote
 * "$88,000.00") so deterministic host-side formatting is more reliable.
 *
 * Expected JSON shape (from AssistantTools.summarizeExpenses):
 *   { "period":"month", "total":"123.45", "count":12,
 *     "byCategory":[{"category":"Food","total":"45.67"}, ...],
 *     "topItems":[{"description":"Lunch","amount":"12.50","date":"2026-05-01","category":"Food"}, ...] }
 */
private fun buildExpensesSummary(context: android.content.Context, toolJson: String): String {
    return try {
        val obj = org.json.JSONObject(toolJson)
        val period = obj.optString("period", "month")
        val total = obj.optString("total", "0.00")
        val count = obj.optInt("count", 0)
        val byCat = obj.optJSONArray("byCategory") ?: org.json.JSONArray()
        val topItems = obj.optJSONArray("topItems") ?: org.json.JSONArray()

        if (count == 0) {
            return context.getString(R.string.assistant_expenses_none, period)
        }

        val sb = StringBuilder()
        sb.append(context.getString(R.string.assistant_expenses_total, total, count, period))
        if (byCat.length() > 0) {
            sb.append("\n\n")
            sb.append(context.getString(R.string.assistant_expenses_by_category_header))
            sb.append("\n")
            for (i in 0 until byCat.length()) {
                val c = byCat.getJSONObject(i)
                sb.append("- **")
                sb.append(c.optString("category", "Other"))
                sb.append("**: ")
                sb.append(c.optString("total", "0.00"))
                sb.append("\n")
            }
        }
        if (topItems.length() > 0) {
            sb.append("\n")
            sb.append(context.getString(R.string.assistant_expenses_top_header))
            sb.append("\n")
            for (i in 0 until topItems.length()) {
                val it = topItems.getJSONObject(i)
                sb.append("- ")
                sb.append(it.optString("description", "?"))
                sb.append(" — ")
                sb.append(it.optString("amount", "0.00"))
                val date = it.optString("date", "")
                if (date.isNotBlank()) {
                    sb.append(" (")
                    sb.append(date)
                    sb.append(")")
                }
                sb.append("\n")
            }
        }
        sb.toString().trimEnd()
    } catch (_: Exception) {
        context.getString(R.string.assistant_expenses_generic)
    }
}

/**
 * Compact AssistChip with a leading icon, used in the quick-action row above
 * the input field. Disabled state visually dims the chip so users know they
 * need to type something first.
 */
@Composable
private fun QuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

/**
 * Execute one assistant turn: build snapshot → Gemma → optional tool → answer
 * (or action proposal).
 *
 * Streams partial text via [onChunk] as tokens arrive. JSON-shaped output (tool
 * calls or wrapped answers / actions) is parsed atomically at the end so the
 * user never sees raw JSON in the chat — chunks only flow when the model is
 * producing free-text markdown inside the `text` field.
 *
 * Must be called on a background dispatcher.
 */
private suspend fun runAssistantTurn(
    context: android.content.Context,
    allMessages: List<ChatMessage>,
    userText: String,
    onChunk: (String) -> Unit
): TurnResult {
    // 1. Build knowledge snapshot. We always build it because the post-turn
    // `buildContextRefs` consumes it to surface clickable references. But
    // when an attached document is in play, the JSON we pass to Gemma is an
    // empty stub — the user's notes / expenses / health are irrelevant noise
    // for doc Q&A, and skipping them frees ~1-2 KB of context budget for the
    // doc itself.
    val snapshot = KnowledgeSnapshot.build(context)
    val snapshotJson = if (AssistantSession.attachedDocId.isNullOrBlank()) {
        snapshot.toJson()
    } else {
        "{}"
    }

    // 1b. If a vault document is attached to this session, load its OCR text
    // and prepend it to the prompt as DOCUMENT CONTEXT. This bypasses the
    // tool-call dance — Gemma 4 E2B can't reliably echo a 13-digit doc id
    // back through a structured tool call, but it CAN read text that's
    // already in its context. Phase 0 of the "Ask My Documents" spike.
    val attachedDocId = AssistantSession.attachedDocId
    val attachedOcr: String? = if (!attachedDocId.isNullOrBlank()) {
        try {
            val crypto = com.privateai.camera.security.CryptoManager(context)
            if (crypto.initialize()) {
                val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                // Find the doc across all categories AND all custom folders.
                // Smart Scanner moves the saved PDF out of the SCAN category
                // and into the user-picked folder; the previous lookup only
                // walked category dirs, so the Assistant couldn't find the
                // OCR sidecar after a folder move.
                val fromCategories = com.privateai.camera.security.VaultCategory.entries.asSequence()
                    .flatMap { vaultRepo.listPhotos(it).asSequence() }
                val folderManager = com.privateai.camera.security.FolderManager(context, crypto)
                val fromFolders = folderManager.listAllFolders().asSequence().flatMap { f ->
                    vaultRepo.listFolderItems(folderManager.getFolderDir(f.id)).asSequence()
                }
                val doc = (fromCategories + fromFolders).firstOrNull { it.id == attachedDocId }
                doc?.let { vaultRepo.loadOcr(it) }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load attached doc OCR: ${e.message}")
            null
        }
    } else null

    // 2. Assemble recent chat history — 8 exchanges (16 messages) for real follow-up context
    val history = allMessages
        .dropLast(1)
        .takeLast(16)
        .map { msg ->
            when (msg) {
                is ChatMessage.User -> "user" to msg.text
                is ChatMessage.Assistant -> "assistant" to msg.text
            }
        }

    // 3. Dynamic temperature — creative tasks get warmer output, data queries
    // stay precise. When an attached doc is in play, force a cooler temp:
    // doc Q&A is factual / extractive, and warmer sampling pushes Gemma 4 E2B
    // into degenerate repetition loops (same summary block emitted forever).
    val temp = if (!AssistantSession.attachedDocId.isNullOrBlank()) 0.2
               else classifyTemperature(userText)

    // 4. First turn — stream and decide free-text vs JSON-wrapped on the fly
    val basePrompt = AssistantPrompts.formatTurn(snapshotJson, history, userText)
    val prompt = if (attachedOcr != null) {
        // 5 KB budget. The snapshot is now empty `{}` when a doc is attached
        // (see step 1), so the freed context goes back to the doc. A 7 KB
        // doc was previously over-truncated to 3 KB; now ~70% of it reaches
        // the model. Real fix for long docs is still Phase 1 (RAG).
        val docBudget = 5120
        val clipped = if (attachedOcr.length > docBudget) attachedOcr.take(docBudget) else attachedOcr
        val truncatedNote = if (attachedOcr.length > docBudget)
            "\n[Note: only the first part of this document is shown (${clipped.length}/${attachedOcr.length} chars). If the answer isn't in this excerpt, say so rather than guessing.]"
        else ""
        // Detect the dominant language of the user's question — used to nudge
        // the model away from defaulting to English when the UI is Arabic /
        // the document is French / etc.
        val langHint = describeLanguage(userText)
        buildString {
            append("ATTACHED DOCUMENT (id: $attachedDocId):\n")
            append(clipped)
            append(truncatedNote)
            append("\n\n---\n\n")
            // Grounding rules — small-model assistants on noisy OCR text
            // reliably hallucinate digits and reformat values. Forcing
            // verbatim quoting + an "I can't find it" escape valve is the
            // cheapest mitigation before we add real RAG citations.
            append("Use the attached document above as your primary source when the user asks about it.\n")
            append("Critical rules:\n")
            append("- Copy any number, date, name, or value EXACTLY as it appears in the document text. Do NOT reformat, normalize, or paraphrase numbers — copy the digits character-by-character.\n")
            append("- When the user asks for a value, quote the exact phrase from the document where you found it.\n")
            append("- If the answer isn't clearly in the text, say \"I can't find that in the document\" rather than guessing.\n")
            append("- The document text above came from OCR and may contain layout artefacts (extra spaces, mis-ordered columns). Don't try to fix or re-render it — work with what's there.\n")
            append("- The OCR can only read Latin scripts. If the source document also contained Arabic, Hebrew, Chinese, Japanese, Korean, or other non-Latin text, those parts are MISSING from the text above. If a section looks short or incomplete, say so — do NOT invent or extrapolate content that isn't visible.\n")
            append("- Reply in the same language as the user's question")
            if (langHint != null) append(" ($langHint)")
            append(". Do NOT default to English unless the user wrote in English. If the user's message is too short to tell, match the document's language instead.\n")
            append("- Keep answers compact. Don't list every section of the document if the user asked about one specific thing. Long replies get cut off mid-sentence — be concise.\n\n")
            append(basePrompt)
        }
    } else basePrompt
    val rawFirst = streamAndCollect(context, prompt, temp, onChunk)
    Log.d(TAG, "First reply (raw): ${rawFirst.take(200)}")

    val firstReply = ParsedReply.parse(rawFirst)
    val contextRefs = buildContextRefs(snapshot, userText)

    return when (firstReply) {
        is ParsedReply.Answer -> TurnResult(firstReply.text, contextRefs)

        is ParsedReply.ActionProposal -> TurnResult(firstReply.text, contextRefs, firstReply.action)

        is ParsedReply.ToolCall -> {
            // 5. Execute the tool
            val crypto = CryptoManager(context).also { it.initialize() }
            val noteRepo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)
            val insightsRepo = com.privateai.camera.security.InsightsRepository(
                File(context.filesDir, "vault/insights"), crypto
            )

            var toolRefs = emptyList<DataRef>()
            val toolResult = when (firstReply.name) {
                "search_notes" -> {
                    val resultJson = AssistantTools.searchNotes(noteRepo, firstReply.query)
                    try {
                        val arr = org.json.JSONArray(resultJson)
                        toolRefs = (0 until arr.length()).mapNotNull { i ->
                            val obj = arr.optJSONObject(i)
                            val title = obj?.optString("title", "") ?: ""
                            val note = noteRepo.listNotes().find { it.title == title }
                            if (note != null && title.isNotBlank()) DataRef(RefKind.NOTE, note.id, title) else null
                        }
                    } catch (_: Exception) {}
                    resultJson
                }
                "fetch_note" -> {
                    val resultJson = AssistantTools.fetchNote(noteRepo, firstReply.query)
                    try {
                        val obj = org.json.JSONObject(resultJson)
                        val title = obj.optString("title", "")
                        if (title.isNotBlank()) toolRefs = listOf(DataRef(RefKind.NOTE, firstReply.query, title))
                    } catch (_: Exception) {}
                    resultJson
                }
                "summarize_expenses" -> {
                    AssistantTools.summarizeExpenses(insightsRepo, firstReply.query)
                }
                "summarize_document" -> {
                    val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                    AssistantTools.summarizeDocument(vaultRepo, firstReply.query)
                }
                "ask_document" -> {
                    val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                    AssistantTools.askDocument(vaultRepo, firstReply.query)
                }
                "search_photos" -> {
                    val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                    val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                    val pi = com.privateai.camera.security.PhotoIndex(db)
                    val contactRepo = com.privateai.camera.security.ContactRepository(
                        java.io.File(context.filesDir, "vault/contacts"),
                        crypto,
                        db
                    )
                    val folderMgr = com.privateai.camera.security.FolderManager(context, crypto)
                    AssistantTools.searchPhotos(pi, contactRepo, vaultRepo, folderMgr, firstReply.query)
                }
                else -> "[]"
            }
            Log.d(TAG, "Tool '${firstReply.name}' result: ${toolResult.take(200)}, refs=${toolRefs.size}")

            // search_photos: decode the base64 thumbnails the tool returned so
            // the host can paint them in the next assistant bubble. Done here
            // (not in the tool fn) because PhotoThumb carries a live Bitmap,
            // not a serializable shape.
            //
            // We also build a "lean" tool result with the thumbs stripped out
            // before passing it to formatToolFollowup — base64 JPEGs at
            // ~10-30 KB each blow past Gemma 4 E2B's context budget when 3+
            // are included, which produced a silent blank reply ("I couldn't
            // generate a response..."). The model only needs the count and
            // the detected-person summary to write its one-line answer.
            var leanToolResult = toolResult
            val turnPhotoThumbs: List<PhotoThumb> = if (firstReply.name == "search_photos") {
                try {
                    val obj = org.json.JSONObject(toolResult)
                    val arr = obj.optJSONArray("photos") ?: org.json.JSONArray()
                    val out = mutableListOf<PhotoThumb>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val id = item.optString("id", "")
                        val b64 = item.optString("thumb", "")
                        if (id.isBlank() || b64.isBlank()) continue
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        out.add(PhotoThumb(id, bmp))
                    }
                    // Rebuild the JSON for the model with the thumb bytes
                    // stripped. Only `total` is reported back — exposing
                    // `photosShown` (the count of base64 thumbs rendered in
                    // the chat) made the model write awkward summaries like
                    // "Total photos found: 87. Thumbnails shown: 6." instead
                    // of one natural sentence.
                    leanToolResult = org.json.JSONObject().apply {
                        put("detectedPerson", obj.opt("detectedPerson") ?: org.json.JSONObject.NULL)
                        put("residualQuery", obj.optString("residualQuery", ""))
                        put("detectedDate", obj.opt("detectedDate") ?: org.json.JSONObject.NULL)
                        put("total", obj.optInt("total", 0))
                    }.toString()
                    out
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode search_photos thumbs: ${e.message}")
                    emptyList()
                }
            } else emptyList()

            // 6. Second turn with tool results — also streams.
            // EXCEPT for search_photos: Gemma routinely mis-reads digits in
            // the tool result ("total:87" → wrote "**7 photos**"). The
            // thumb strip already shows the visual evidence; the text just
            // confirms the count. Build it deterministically host-side to
            // avoid the number-hallucination class of bug entirely.
            val answerText: String
            val secondAction: ProposedAction?
            if (firstReply.name == "search_photos") {
                answerText = buildSearchPhotosSummary(context, leanToolResult, firstReply.query, turnPhotoThumbs.size)
                secondAction = null
                // Stream the host-built summary to the partial-message UI so the
                // bubble doesn't blank out between the typing dot and the final
                // text. One emit is sufficient — the text is short.
                onChunk(answerText)
            } else if (firstReply.name == "summarize_expenses") {
                // Same anti-hallucination strategy as search_photos: Gemma
                // mis-copies currency digits from the JSON. Host-built
                // formatting is deterministic.
                answerText = buildExpensesSummary(context, toolResult)
                secondAction = null
                onChunk(answerText)
            } else {
                // Note: for the other tools the full toolResult string is
                // safe (no base64 payload). For search_photos we'd use the
                // leanToolResult but we've already bypassed this branch.
                val followUp = AssistantPrompts.formatToolFollowup(
                    snapshotJson, userText, firstReply.name, firstReply.query, toolResult
                )
                val rawSecond = streamAndCollect(context, followUp, temp, onChunk)
                Log.d(TAG, "Second reply (raw): ${rawSecond.take(200)}")
                val secondReply = ParsedReply.parse(rawSecond)
                when (secondReply) {
                    is ParsedReply.Answer -> { answerText = secondReply.text; secondAction = null }
                    is ParsedReply.ActionProposal -> { answerText = secondReply.text; secondAction = secondReply.action }
                    is ParsedReply.ToolCall -> { answerText = "I found some results but couldn't summarize them. Please try rephrasing."; secondAction = null }
                }
            }
            TurnResult(answerText, toolRefs.ifEmpty { contextRefs }, secondAction, turnPhotoThumbs)
        }
    }
}

/**
 * Drive [GemmaRunner.completeStreaming] with progressive parsing.
 *
 * The system prompt instructs the model to wrap every reply in
 * `{"type":"answer","text":"..."}` JSON. Naively forwarding chunks would flash
 * raw JSON in the chat; suppressing all JSON-shaped output would suppress
 * everything. So we incrementally extract the value of the `text` field as it
 * streams and emit *that* via [onChunk], decoding JSON escape sequences as we
 * go. Tool calls are detected up front and suppressed entirely (they're parsed
 * once complete and routed to a tool, not shown to the user).
 *
 * If the model unexpectedly emits raw markdown (no `{` wrapper), we forward
 * chunks directly.
 *
 * Returns the full raw text so the caller can run [ParsedReply.parse] to
 * detect tool calls vs cleaned answers and run final cleanup.
 */
private suspend fun streamAndCollect(
    context: android.content.Context,
    prompt: String,
    temperature: Double,
    onChunk: (String) -> Unit
): String {
    val raw = StringBuilder()

    // Decision state. Settled on the first non-whitespace char.
    var mode: StreamMode = StreamMode.UNDECIDED
    // For JSON_WRAPPED mode: where the text field's value begins (just after `"text":"`).
    var textValueStart = -1
    var lastEmittedLen = 0
    var textFieldClosed = false

    GemmaRunner.completeStreaming(
        context, prompt, AssistantPrompts.SYSTEM, temperature
    ).collect { chunk ->
        raw.append(chunk)

        // Repetition-loop guard. Gemma 4 E2B sometimes falls into a degenerate
        // state where it emits the same block over and over until cancelled.
        // If we see a 60-char suffix appear 3+ times in the last ~600 chars of
        // output, cut the stream — better a truncated answer than an infinite
        // loop draining battery and pinning the user on a "thinking…" state.
        if (raw.length >= 300) {
            val tail = raw.substring(raw.length - 60)
            val window = raw.substring(maxOf(0, raw.length - 600))
            if (tail.isNotBlank() && occurrences(window, tail) >= 3) {
                Log.w(TAG, "Repetition loop detected (60-char tail seen 3× in last 600 chars) — cutting stream")
                kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                return@collect
            }
        }

        if (mode == StreamMode.UNDECIDED) {
            val trimmed = raw.toString().trimStart()
            if (trimmed.isEmpty()) return@collect
            val first = trimmed[0]
            mode = if (first == '{' || (first == '`' && trimmed.startsWith("```"))) {
                StreamMode.JSON_WRAPPED
            } else {
                StreamMode.RAW
            }
        }

        when (mode) {
            StreamMode.RAW -> {
                onChunk(raw.toString())
            }
            StreamMode.JSON_WRAPPED -> {
                // Tool calls have no "text" field, so the marker below never
                // matches and nothing gets emitted — natural suppression.
                if (textValueStart < 0) {
                    // Tolerate `"text" : "` with arbitrary whitespace (the model
                    // sometimes emits pretty-printed JSON wrapped in ```json).
                    val match = TextFieldMarker.find(raw)
                    if (match == null) return@collect
                    textValueStart = match.range.last + 1
                }

                if (textFieldClosed) return@collect

                val sub = raw.substring(textValueStart)
                val (decoded, closed) = extractJsonStringPartial(sub)
                if (decoded.length > lastEmittedLen) {
                    onChunk(decoded)
                    lastEmittedLen = decoded.length
                }
                if (closed) textFieldClosed = true
            }
            else -> {}
        }
    }

    return raw.toString()
}

private enum class StreamMode { UNDECIDED, RAW, JSON_WRAPPED }

/** Cheap substring-occurrence count for the repetition-loop guard. */
private fun occurrences(haystack: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var i = 0
    while (true) {
        val idx = haystack.indexOf(needle, i)
        if (idx < 0) break
        count++
        i = idx + needle.length
    }
    return count
}

/** Matches the start of a JSON `"text": "..."` field with arbitrary whitespace. */
private val TextFieldMarker = Regex("\"text\"\\s*:\\s*\"")

/**
 * Decode a JSON string-literal value progressively. [input] is the substring
 * starting *after* the opening quote. Returns (decoded text so far, whether
 * the closing quote has been reached). Stops cleanly at an incomplete escape
 * sequence so we don't emit half-decoded characters.
 */
private fun extractJsonStringPartial(input: String): Pair<String, Boolean> {
    val sb = StringBuilder()
    var i = 0
    val n = input.length
    while (i < n) {
        val c = input[i]
        when {
            c == '\\' -> {
                if (i + 1 >= n) return sb.toString() to false  // wait for next chunk
                val esc = input[i + 1]
                when (esc) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append(''); i += 2 }
                    'u' -> {
                        if (i + 5 >= n) return sb.toString() to false
                        val hex = input.substring(i + 2, i + 6)
                        try {
                            sb.append(hex.toInt(16).toChar())
                        } catch (_: Exception) {
                            sb.append('?')
                        }
                        i += 6
                    }
                    else -> { sb.append(esc); i += 2 }
                }
            }
            c == '"' -> return sb.toString() to true  // closing quote
            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString() to false
}

/**
 * Best-effort language hint to nudge Gemma away from defaulting to English
 * (or the document's language) when the user is asking in something else.
 * Returns null when we genuinely can't tell — the model's general "match
 * the user's language" rule still applies in that case.
 *
 * Strategy:
 *  1. Non-Latin scripts (Arabic, CJK, Cyrillic, etc.) — return the script's
 *     language directly. High-confidence signal.
 *  2. Latin script — count uniquely-French / uniquely-Spanish stop-words
 *     (i.e. *not* "document" or "resume" which collide with English). Only
 *     commit if at least 2 hit, otherwise tie-breaks fall to the wrong side.
 *  3. Final fallback — the device's display locale. If Privora's UI is set
 *     to English, short queries like "Summarize this document." get tagged
 *     English; if UI is French, they get tagged French.
 */
private fun describeLanguage(userText: String): String? {
    // 1. Script detection — fast and reliable.
    val firstNonAscii = userText.firstOrNull { it.code > 127 }
    if (firstNonAscii != null) {
        val block = Character.UnicodeBlock.of(firstNonAscii)
        when (block) {
            Character.UnicodeBlock.ARABIC,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B -> return "Arabic"
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS -> return "Chinese"
            Character.UnicodeBlock.CYRILLIC -> return "Russian"
            Character.UnicodeBlock.HIRAGANA, Character.UnicodeBlock.KATAKANA -> return "Japanese"
            Character.UnicodeBlock.HANGUL_SYLLABLES -> return "Korean"
            else -> {}
        }
    }
    // 2. Latin-script: only uniquely-French / uniquely-Spanish markers count.
    // Words that collide with English ("document", "resume") are excluded so
    // a short English question like "Summarize this document." doesn't get
    // mis-tagged as French.
    val lower = " ${userText.lowercase()} "
    val frenchUnique = listOf(" le ", " les ", " une ", " et ", " est ", " du ", " des ", " que ", " qu'", " ce ", " cette ", "résume", "résumé", "français", "à propos")
    val spanishUnique = listOf(" el ", " los ", " las ", " los ", " es ", " del ", " esta ", " este ", " con ", " por ", " para ", "español", "qué")
    val frenchHits = frenchUnique.count { lower.contains(it) }
    val spanishHits = spanishUnique.count { lower.contains(it) }
    if (frenchHits >= 2 && frenchHits > spanishHits) return "French"
    if (spanishHits >= 2 && spanishHits > frenchHits) return "Spanish"
    // 3. Fallback: device locale.
    return when (java.util.Locale.getDefault().language) {
        "en" -> "English"
        "fr" -> "French"
        "es" -> "Spanish"
        "ar" -> "Arabic"
        "zh" -> "Chinese"
        else -> null  // unknown — let Gemma's general rule handle it
    }
}

/** Classify user intent → temperature. Creative tasks need warmer, data queries need colder. */
private fun classifyTemperature(userText: String): Double {
    val lower = userText.lowercase()
    val creativeKeywords = listOf(
        "summarize", "rewrite", "draft", "translate", "fix grammar", "write",
        "compose", "explain", "expand", "shorten", "formal", "casual",
        "creative", "improve", "rephrase", "email", "letter", "message"
    )
    return if (creativeKeywords.any { lower.contains(it) }) 0.7 else 0.3
}

/**
 * Build tappable references from the snapshot for the **single most relevant** topic.
 * Only one category of refs per answer — avoids flooding the UI with reminders+habits+health
 * when the user's question is specifically about one thing.
 *
 * For broad queries like "what did I do this week?" the AI's text answer covers multiple
 * categories; the refs link to the single strongest match so the user can drill in.
 */
private fun buildContextRefs(
    snapshot: KnowledgeSnapshot,
    userText: String
): List<DataRef> {
    val lower = userText.lowercase()

    // Score each category — pick the one with the strongest keyword match
    val reminderScore = lower.countKeywords("reminder", "schedule", "upcoming", "coming up", "next week", "alarm")
    val habitScore = lower.countKeywords("habit", "streak", "routine", "completion")
    val healthScore = lower.countKeywords("health", "weight", "blood", "heart", "sleep", "bp", "pressure", "mood")
    val noteScore = lower.countKeywords("note", "notes")

    // Pick the winner — ties go to the first match; 0 = no refs (pure text task)
    data class Scored(val kind: String, val score: Int)
    val best = listOf(
        Scored("reminder", reminderScore),
        Scored("habit", habitScore),
        Scored("health", healthScore),
        Scored("note", noteScore)
    ).filter { it.score > 0 }.maxByOrNull { it.score } ?: return emptyList()

    return when (best.kind) {
        "reminder" -> snapshot.reminders.take(8).map {
            DataRef(RefKind.REMINDER, it.title, "${it.title} — ${it.dateTime}")
        }
        "habit" -> snapshot.habits.take(8).map {
            DataRef(RefKind.HABIT, it.name, "${it.name} (${it.last30}/30d)")
        }
        "health" -> snapshot.healthLast7.take(5).map {
            val label = buildString {
                append(it.date)
                it.weight?.let { w -> append(" • ${"%.1f".format(w)}kg") }
                it.heartRate?.let { h -> append(" • ${h}bpm") }
                it.bp?.let { b -> append(" • $b") }
            }
            DataRef(RefKind.HEALTH, it.date, label)
        }
        else -> emptyList() // notes handled via tool call, not snapshot refs
    }
}

/** Count how many of the given keywords appear in this string. */
private fun String.countKeywords(vararg keywords: String): Int =
    keywords.count { this.contains(it) }

