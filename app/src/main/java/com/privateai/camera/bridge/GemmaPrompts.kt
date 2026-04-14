package com.privateai.camera.bridge

/**
 * Prompt templates for Gemma 4 notes intelligence features.
 * All prompts are engineered for concise, actionable output.
 */
object GemmaPrompts {

    /** System instruction for all notes-related tasks. */
    const val NOTES_SYSTEM = "You are a helpful writing assistant inside a private notes app. " +
            "Be concise and direct. Never include explanations unless asked. " +
            "Output only the requested content, no preamble. " +
            "Always respond in the same language as the user's input text."

    /** Summarize a long note into bullet points. */
    fun summarize(noteContent: String): String = buildString {
        append("Summarize the following note into 3-5 concise bullet points. ")
        append("Use '• ' prefix for each point. Output only the bullet points.\n\n")
        append(noteContent)
    }

    /** Generate a title from note body. Must match the note's language. */
    fun generateTitle(noteContent: String): String = buildString {
        append("Generate a short title (3-8 words) for this note. ")
        append("The title MUST be in the same language as the note content. ")
        append("Output only the title, nothing else.\n\n")
        append(noteContent.take(500))
    }

    /** Extract actionable tasks from free text into checklist format. */
    fun extractChecklist(noteContent: String): String = buildString {
        append("Extract all actionable tasks from this text. ")
        append("Output each task on a new line in this exact format: - [ ] task description\n")
        append("If no tasks found, output the original text unchanged.\n\n")
        append(noteContent)
    }

    /** Smart rewrite — change tone or style. */
    fun rewrite(text: String, style: RewriteStyle): String = buildString {
        append(when (style) {
            RewriteStyle.SHORTEN -> "Rewrite this text to be shorter and more concise. Keep the meaning."
            RewriteStyle.EXPAND -> "Expand this text with more detail and explanation."
            RewriteStyle.FORMAL -> "Rewrite this text in a formal, professional tone."
            RewriteStyle.CASUAL -> "Rewrite this text in a casual, friendly tone."
            RewriteStyle.FIX_GRAMMAR -> "Fix all grammar, spelling, and punctuation errors in this text. Output only the corrected text."
        })
        append("\n\n")
        append(text)
    }

    /** Continue writing from where the user left off. */
    fun continueWriting(noteContent: String): String = buildString {
        append("Continue writing this note naturally from where it left off. ")
        append("Write 2-3 sentences that follow the same style and topic.\n\n")
        append(noteContent)
    }

    /** Describe an image for the vault photo index. */
    fun describePhoto(): String {
        return "Describe this image in one concise sentence. Focus on the main subject, activity, and setting."
    }
}

enum class RewriteStyle {
    SHORTEN,
    EXPAND,
    FORMAL,
    CASUAL,
    FIX_GRAMMAR
}
