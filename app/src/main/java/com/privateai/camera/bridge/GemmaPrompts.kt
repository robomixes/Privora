// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

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

    /** Describe an image for the vault photo index. Localized to the device language. */
    fun describePhoto(): String {
        val lang = uiLanguageName()
        return "Describe this image in one concise sentence. " +
                "Focus on the main subject, activity, and setting. " +
                "Reply in $lang."
    }

    /**
     * Generate short, lowercase, comma-separated tags for an image.
     *
     * The reply is parsed by splitting on commas and trimming each token —
     * the prompt is deliberately strict about format because the parser is
     * intentionally dumb (no JSON, no full sentences, no enumeration markers).
     *
     * Tag words themselves are emitted in the user's UI language so they're
     * usable as search keywords later (a French user shouldn't have to search
     * "cat" instead of "chat"). **Exception**: Arabic falls back to English
     * tag words — Gemma's structured-output reliability drops in Arabic (often
     * returns a full sentence instead of a comma list, or mixes RTL/LTR
     * fragments that the parser drops). The descriptive sentence in
     * `describePhoto()` is still emitted in Arabic; only the strict
     * comma-list format is moved to English here.
     */
    fun generateTags(): String {
        val lang = if (java.util.Locale.getDefault().language == "ar") "English" else uiLanguageName()
        return "List 5 to 8 short tags in $lang for this image describing the subject, objects, " +
                "setting, mood, and time of day. Output only the tags, comma-separated, " +
                "lowercase, no punctuation other than commas, no full sentences, no numbering."
    }

    /**
     * Wrap a user-typed image question with a "Reply in <UI language>" hint
     * so the answer matches the UI language (the user's typed question may
     * itself be short / ambiguous about language).
     */
    fun askAboutImage(question: String): String {
        val lang = uiLanguageName()
        return "$question\n\nReply in $lang."
    }

    /**
     * Prompt for the Detect module's per-detection "Describe with AI" action.
     * YOLO has already named the object coarsely ([yoloClass]); Gemma's job
     * is to refine — make/model for cars, breed/species for animals, type
     * for objects — in one concise sentence in the UI language.
     */
    fun describeDetection(yoloClass: String): String {
        val lang = uiLanguageName()
        return "This image shows a $yoloClass. " +
                "Identify it more specifically — e.g. make and model for a car, " +
                "breed or species for an animal, brand or type for an object. " +
                "Reply in one concise sentence in $lang."
    }

    /**
     * Map the active app locale to a Gemma-friendly English name. Used as a
     * `Reply in <X>` hint in vision prompts so the description / tags match
     * the UI language. Reads `Locale.getDefault()` which AppCompat keeps in
     * sync with the per-app language picker.
     */
    private fun uiLanguageName(): String {
        return when (java.util.Locale.getDefault().language) {
            "fr" -> "French"
            "es" -> "Spanish"
            "zh" -> "Chinese"
            "ar" -> "Arabic"
            "en" -> "English"
            else -> java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH)
        }
    }
}

enum class RewriteStyle {
    SHORTEN,
    EXPAND,
    FORMAL,
    CASUAL,
    FIX_GRAMMAR
}
