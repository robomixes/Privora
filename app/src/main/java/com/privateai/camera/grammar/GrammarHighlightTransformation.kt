package com.privateai.camera.grammar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * VisualTransformation that underlines grammar/spelling errors.
 * Works with the standard BasicTextField(value, onValueChange) API.
 * Does not modify text — only adds visual underline annotations.
 */
class GrammarHighlightTransformation(
    private val errors: List<GrammarError>,
    private val underlineColor: Color = Color(0xFFE53935) // Red 600
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val annotated = buildAnnotatedString {
            append(text)
            for (error in errors) {
                if (error.fromPos < text.length && error.toPos <= text.length && error.fromPos < error.toPos) {
                    addStyle(
                        style = SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            background = underlineColor.copy(alpha = 0.12f)
                        ),
                        start = error.fromPos,
                        end = error.toPos
                    )
                    // Tag the error range so we can identify it on tap
                    addStringAnnotation(
                        tag = "grammar_error",
                        annotation = error.ruleId,
                        start = error.fromPos,
                        end = error.toPos
                    )
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
