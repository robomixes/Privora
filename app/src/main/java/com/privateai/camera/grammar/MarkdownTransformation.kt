// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.grammar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * Renders inline Markdown in-place:
 * - **bold** → bold text, markers dimmed
 * - *italic* → italic text, markers dimmed
 * - __underline__ → underlined text, markers dimmed
 * - ~~strikethrough~~ → strikethrough text, markers dimmed
 *
 * Markers remain visible (dimmed) so the user can edit them.
 * Combine with GrammarHighlightTransformation by chaining.
 */
class MarkdownTransformation(
    private val markerColor: Color = Color.Gray.copy(alpha = 0.35f),
    private val grammarErrors: List<GrammarError> = emptyList(),
    private val errorColor: Color = Color.Red
) : VisualTransformation {

    // Patterns ordered by marker length (longest first to avoid conflicts)
    private val patterns = listOf(
        // **bold** (must come before *italic* to avoid partial match)
        InlinePattern("\\*\\*(.+?)\\*\\*", SpanStyle(fontWeight = FontWeight.Bold), 2),
        // ~~strikethrough~~
        InlinePattern("~~(.+?)~~", SpanStyle(textDecoration = TextDecoration.LineThrough), 2),
        // __underline__
        InlinePattern("__(.+?)__", SpanStyle(textDecoration = TextDecoration.Underline), 2),
        // *italic* (single asterisk, but not inside **)
        InlinePattern("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", SpanStyle(fontStyle = FontStyle.Italic), 1),
    )

    private data class InlinePattern(
        val regex: String,
        val style: SpanStyle,
        val markerLen: Int
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            append(raw)

            // Apply Markdown styling
            for (pattern in patterns) {
                val regex = Regex(pattern.regex)
                regex.findAll(raw).forEach { match ->
                    val fullRange = match.range
                    val start = fullRange.first
                    val end = fullRange.last + 1
                    val mLen = pattern.markerLen

                    // Dim the opening marker
                    addStyle(
                        SpanStyle(color = markerColor, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal),
                        start, start + mLen
                    )
                    // Style the content between markers
                    addStyle(pattern.style, start + mLen, end - mLen)
                    // Dim the closing marker
                    addStyle(
                        SpanStyle(color = markerColor, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal),
                        end - mLen, end
                    )
                }
            }

            // Apply grammar error underlines on top
            for (error in grammarErrors) {
                if (error.fromPos < raw.length && error.toPos <= raw.length && error.fromPos < error.toPos) {
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            background = errorColor.copy(alpha = 0.12f)
                        ),
                        error.fromPos, error.toPos
                    )
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
