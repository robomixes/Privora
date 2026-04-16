package com.privateai.camera.ui.assistant

import android.util.Log
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.bridge.AssistantPrompts
import com.privateai.camera.bridge.AssistantTools
import com.privateai.camera.bridge.GemmaRunner
import com.privateai.camera.bridge.KnowledgeSnapshot
import com.privateai.camera.bridge.ParsedReply
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.NoteRepository
import kotlinx.coroutines.Dispatchers
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: (() -> Unit)? = null,
    onNavigate: ((route: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = AssistantSession.messages
    var thinking by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Pre-warm the Gemma engine so the first reply doesn't pay cold-load latency
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { GemmaRunner.load(context) }
    }

    // Auto-scroll to bottom when a new message is added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || thinking) return
        inputText = ""
        messages += ChatMessage.User(text)
        thinking = true

        scope.launch {
            try {
                val (answerText, refs) = withContext(Dispatchers.IO) { runAssistantTurn(context, messages, text) }
                messages += ChatMessage.Assistant(answerText, refs)
            } catch (e: Exception) {
                Log.e(TAG, "Assistant error: ${e.message}", e)
                messages += ChatMessage.Assistant(context.getString(R.string.assistant_error_generic))
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
                        IconButton(onClick = { messages.clear() }) {
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

                items(messages) { msg ->
                    ChatBubble(msg, onRefClick = { ref ->
                        val route = when (ref.kind) {
                            RefKind.NOTE -> "notes?openNoteId=${ref.id}"
                            RefKind.REMINDER -> "reminders"
                            RefKind.HABIT -> "insights?tab=habits"
                            RefKind.HEALTH -> "insights?tab=health"
                        }
                        onNavigate?.invoke(route)
                    })
                }

                if (thinking) {
                    item { ThinkingBubble() }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }

            // Input bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),   // enough for 2 visible lines at rest
                    placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                    minLines = 2,
                    maxLines = 15,                 // expands for pasted emails / long text
                    enabled = !thinking
                )
                IconButton(
                    onClick = { sendMessage() },
                    enabled = inputText.isNotBlank() && !thinking
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.assistant_send),
                        tint = if (inputText.isNotBlank() && !thinking)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * Execute one assistant turn: build snapshot → Gemma → optional tool → answer.
 * Returns the answer text + any note references from a search_notes tool call.
 * Must be called on a background dispatcher.
 */
private suspend fun runAssistantTurn(
    context: android.content.Context,
    allMessages: List<ChatMessage>,
    userText: String
): Pair<String, List<DataRef>> {
    // 1. Build knowledge snapshot
    val snapshot = KnowledgeSnapshot.build(context)
    val snapshotJson = snapshot.toJson()

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

    // 3. Dynamic temperature — creative tasks get warmer output, data queries stay precise
    val temp = classifyTemperature(userText)

    // 4. First Gemma call (blocking — need full JSON to detect tool calls)
    val prompt = AssistantPrompts.formatTurn(snapshotJson, history, userText)
    val rawFirst = GemmaRunner.complete(
        context, prompt, AssistantPrompts.SYSTEM,
        maxTokens = 1024, temperature = temp
    )
    Log.d(TAG, "First reply (raw): ${rawFirst?.take(200)}")

    val firstReply = ParsedReply.parse(rawFirst)
    val contextRefs = buildContextRefs(snapshot, userText)

    return when (firstReply) {
        is ParsedReply.Answer -> firstReply.text to contextRefs

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
                    // Add a ref for the fetched note
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
                else -> "[]"
            }
            Log.d(TAG, "Tool '${firstReply.name}' result: ${toolResult.take(200)}, refs=${toolRefs.size}")

            // 6. Second Gemma call with tool results
            val followUp = AssistantPrompts.formatToolFollowup(
                snapshotJson, userText, firstReply.name, firstReply.query, toolResult
            )
            val rawSecond = GemmaRunner.complete(
                context, followUp, AssistantPrompts.SYSTEM,
                maxTokens = 1024, temperature = temp
            )
            Log.d(TAG, "Second reply (raw): ${rawSecond?.take(200)}")

            val secondReply = ParsedReply.parse(rawSecond)
            val answerText = when (secondReply) {
                is ParsedReply.Answer -> secondReply.text
                is ParsedReply.ToolCall -> "I found some results but couldn't summarize them. Please try rephrasing."
            }
            answerText to (toolRefs.ifEmpty { contextRefs })
        }
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

