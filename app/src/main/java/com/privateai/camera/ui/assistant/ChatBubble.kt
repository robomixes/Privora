package com.privateai.camera.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.privateai.camera.R

/** Type of data item the assistant references in its answer. */
enum class RefKind { NOTE, REMINDER, HABIT, HEALTH }

/** A tappable reference to an app data item surfaced in an assistant answer. */
data class DataRef(val kind: RefKind, val id: String, val label: String)

/** A single message in the chat — user or assistant. */
sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Assistant(val text: String, val refs: List<DataRef> = emptyList()) : ChatMessage()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(message: ChatMessage, onRefClick: ((DataRef) -> Unit)? = null) {
    val isUser = message is ChatMessage.User
    val text = when (message) {
        is ChatMessage.User -> message.text
        is ChatMessage.Assistant -> message.text
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            Card(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, R.string.assistant_copied, Toast.LENGTH_SHORT).show()
                        }
                    ),
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            Row(
                modifier = Modifier.widthIn(max = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(top = 4.dp)
                )
                Column(Modifier.weight(1f, fill = false)) {
                    Card(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    clipboard.setText(AnnotatedString(text))
                                    Toast.makeText(context, R.string.assistant_copied, Toast.LENGTH_SHORT).show()
                                }
                            ),
                        shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        MarkdownText(
                            text,
                            modifier = Modifier.padding(12.dp),
                            baseStyle = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Tappable data references (notes, reminders, habits, health)
                    val refs = (message as? ChatMessage.Assistant)?.refs ?: emptyList()
                    if (refs.isNotEmpty() && onRefClick != null) {
                        Column(
                            Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            refs.forEach { ref ->
                                val icon = when (ref.kind) {
                                    RefKind.NOTE -> Icons.Default.NoteAlt
                                    RefKind.REMINDER -> Icons.Default.Notifications
                                    RefKind.HABIT -> Icons.Default.CheckCircle
                                    RefKind.HEALTH -> Icons.Default.FitnessCenter
                                }
                                AssistChip(
                                    onClick = { onRefClick(ref) },
                                    label = { Text(ref.label.take(40), style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pulsing "thinking" indicator shown while the model is generating. */
@Composable
fun ThinkingBubble() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Thinking…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Lightweight markdown renderer for assistant output. Handles the subset Gemma actually produces:
 * - `**bold**` → bold span
 * - `*italic*` → italic span
 * - `- item` or `• item` → bullet with indent
 * - Blank line (`\n\n`) → paragraph break
 * - Line starting with a capitalized word followed by `\n` → section header (bold)
 *
 * NOT a full CommonMark parser — intentionally minimal so edge cases degrade to plain text
 * rather than crashing or producing garbled output.
 */
@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val paragraphs = text.split(Regex("\n{2,}"))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paragraphs.forEach { para ->
            val trimmed = para.trim()
            if (trimmed.isEmpty()) return@forEach
            val lines = trimmed.split("\n")
            lines.forEach { line ->
                val ln = line.trim()
                when {
                    // Bullet point
                    ln.startsWith("- ") || ln.startsWith("• ") -> {
                        Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", style = baseStyle, color = color)
                            Text(
                                buildFormattedString(ln.removePrefix("- ").removePrefix("• ")),
                                style = baseStyle, color = color
                            )
                        }
                    }
                    // Section header — a line that's all letters/spaces, ends before a bullet list
                    ln.isNotEmpty() && !ln.contains("**") && ln.length < 40 && ln.first().isUpperCase() && lines.any { it.trim().startsWith("- ") } -> {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            ln,
                            style = baseStyle.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Regular text with possible inline formatting
                    else -> {
                        Text(buildFormattedString(ln), style = baseStyle, color = color)
                    }
                }
            }
        }
    }
}

/** Parse inline **bold** and *italic* markers into an AnnotatedString. */
@Composable
private fun buildFormattedString(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                // *italic* (but not **)
                text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > 0) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
