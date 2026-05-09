// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.SecureNote
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultRepository
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Parsed reply from the assistant model. Either a final text answer,
 * a tool-call request the host must execute and feed back, or a write
 * action proposed for user confirmation.
 */
sealed class ParsedReply {
    data class Answer(val text: String) : ParsedReply()
    /** A tool call. [query] is the primary param — doubles as query/id/period depending on the tool. */
    data class ToolCall(val name: String, val query: String) : ParsedReply()
    /** A user-data write the model wants to perform. [text] is the chat-bubble copy; [action] is the proposal. */
    data class ActionProposal(val text: String, val action: ProposedAction) : ParsedReply()

    companion object {
        /**
         * Parse the model's JSON output into a [ParsedReply].
         *
         * Tolerant: if the JSON is malformed or doesn't match the schema,
         * falls back to treating the entire raw output as a text answer
         * (so the user still sees something useful — never raw JSON/brackets).
         */
        fun parse(raw: String?): ParsedReply {
            if (raw.isNullOrBlank()) return Answer("I couldn't generate a response. Please try again.")

            // Try to extract JSON object first
            val json = GemmaRunner.extractFirstJsonObject(raw)
            if (json != null) {
                val type = json.optString("type", "answer").lowercase()
                if (type == "tool") {
                    val name = json.optString("name", "")
                    // Tool param can be "query", "id", or "period" depending on the tool
                    val query = json.optString("query", "")
                        .ifEmpty { json.optString("id", "") }
                        .ifEmpty { json.optString("period", "") }
                    if (name.isNotBlank() && query.isNotBlank()) {
                        return ToolCall(name, query)
                    }
                }
                if (type == "action") {
                    val parsed = AssistantActions.parse(json)
                    if (parsed != null) {
                        // Bubble copy: use the model's `summary` (already mirrored into ProposedAction.summary)
                        // or the optional `text` field if the model also wrote one.
                        val bubble = json.optString("text", "").trim().ifEmpty { parsed.summary }
                        return ActionProposal(cleanupText(bubble), parsed)
                    }
                    // Fall through to text extraction if action JSON was malformed
                }
                val text = json.optString("text", "").trim()
                if (text.isNotEmpty()) return Answer(cleanupText(text))
            }

            // Fallback: strip any remaining JSON artifacts and treat as plain text
            return Answer(cleanupText(raw.trim()))
        }

        /**
         * Clean up model output that may contain JSON-like artifacts, code fences,
         * or leftover structural tokens. The user should never see raw brackets,
         * `"type":"answer"` fields, or triple backtick fences.
         *
         * Gemma sometimes outputs malformed "JSON" without quotes:
         *   {type:answer, text: The summary is…}
         * or partially quoted:
         *   {"type":"answer","text":"The summary…}
         * All must be stripped down to just the human-readable text.
         */
        private fun cleanupText(text: String): String {
            var cleaned = text
            // Strip leading/trailing code fences (```json ... ```)
            cleaned = cleaned.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            // If the entire string looks like a JSON object the parser missed, extract "text" field
            if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                try {
                    val obj = org.json.JSONObject(cleaned)
                    val inner = obj.optString("text", "").trim()
                    if (inner.isNotEmpty()) return inner
                } catch (_: Exception) { /* not valid JSON, proceed with regex cleanup */ }
            }

            // Regex: strip {type:answer, text: ...} wrapper (with or without quotes)
            // Handles: {"type":"answer","text":"..."}, {type:answer,text:...}, {"type": "answer", "text": "..."}
            val patterns = listOf(
                // Fully quoted
                Regex("""\{\s*"type"\s*:\s*"answer"\s*,\s*"text"\s*:\s*"(.*?)"\s*\}""", RegexOption.DOT_MATCHES_ALL),
                // Unquoted keys/values — greedy capture after "text:"
                Regex("""\{\s*"?type"?\s*:\s*"?answer"?\s*[,;]\s*"?text"?\s*:\s*"?(.*?)"?\s*\}""", RegexOption.DOT_MATCHES_ALL),
                // Just braces wrapping everything with "text" somewhere inside
                Regex("""\{[^}]*"?text"?\s*:\s*"?(.+?)"?\s*\}""", RegexOption.DOT_MATCHES_ALL)
            )
            for (pattern in patterns) {
                val match = pattern.find(cleaned)
                if (match != null) {
                    val extracted = match.groupValues[1].trim()
                    if (extracted.isNotEmpty() && extracted.length > 10) { // sanity: at least a short sentence
                        return extracted
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .trimEnd('"', '}', ' ')
                    }
                }
            }

            // Last resort: if the text starts with { and ends with }, just strip the outer braces
            // and any "type"/"answer" keywords to salvage readable content
            if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                cleaned = cleaned.removePrefix("{").removeSuffix("}").trim()
                cleaned = cleaned
                    .replace(Regex("""^"?type"?\s*:\s*"?answer"?\s*[,;]?\s*"""), "")
                    .replace(Regex("""^"?text"?\s*:\s*"?"""), "")
                    .trimEnd('"')
                    .trim()
            }

            return cleaned
        }
    }
}

/**
 * Stage 1 tools the assistant can invoke. Each tool reads data and returns
 * a JSON string that gets injected into the follow-up prompt.
 */
object AssistantTools {

    /**
     * Case-insensitive substring search against note titles + bodies.
     * Returns up to 5 matching notes with a title + short snippet around the match.
     */
    fun searchNotes(repo: NoteRepository, query: String): String {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return "[]"

        val hits = repo.listNotes()
            .mapNotNull { note -> scoreNote(note, q)?.let { note to it } }
            .sortedByDescending { it.second }
            .take(5)
            .map { (note, _) ->
                JSONObject().apply {
                    put("title", note.title)
                    put("snippet", extractSnippet(note.content, q, 120))
                    put("tags", JSONArray(note.tags))
                    put("modified", note.modifiedAt)
                }
            }

        return JSONArray(hits).toString()
    }

    /** Score a note against the query. Returns null if no match. */
    private fun scoreNote(note: SecureNote, query: String): Int? {
        val titleMatch = note.title.lowercase().contains(query)
        val bodyMatch = note.content.lowercase().contains(query)
        val tagMatch = note.tags.any { it.lowercase().contains(query) }
        if (!titleMatch && !bodyMatch && !tagMatch) return null
        // Title hits are more valuable than body hits
        return (if (titleMatch) 10 else 0) + (if (tagMatch) 5 else 0) + (if (bodyMatch) 1 else 0)
    }

    /** Extract a snippet of ~maxLen characters centered on the first occurrence of query. */
    private fun extractSnippet(content: String, query: String, maxLen: Int): String {
        val idx = content.lowercase().indexOf(query)
        if (idx < 0) return content.take(maxLen)
        val start = (idx - maxLen / 3).coerceAtLeast(0)
        val end = (start + maxLen).coerceAtMost(content.length)
        val snippet = content.substring(start, end).trim()
        return buildString {
            if (start > 0) append("…")
            append(snippet)
            if (end < content.length) append("…")
        }
    }

    /**
     * Fetch the full content of a note by ID. Returns a JSON object with title + full body.
     * Lets the assistant read, discuss, and summarize specific notes.
     */
    fun fetchNote(repo: NoteRepository, noteId: String): String {
        val note = repo.listNotes().find { it.id == noteId }
            ?: return """{"error":"Note not found"}"""
        return JSONObject().apply {
            put("title", note.title)
            put("content", note.content.take(3000)) // cap to stay within context budget
            put("tags", JSONArray(note.tags))
            put("modified", note.modifiedAt)
        }.toString()
    }

    /**
     * Aggregate expenses for a time period ("week" / "month" / "year").
     * Returns category totals, top items, and the overall total — richer than the snapshot's 30-item cap.
     */
    fun summarizeExpenses(repo: InsightsRepository, period: String): String {
        val now = System.currentTimeMillis()
        val cutoff = when (period.lowercase()) {
            "week" -> now - 7L * 24 * 60 * 60 * 1000
            "year" -> now - 365L * 24 * 60 * 60 * 1000
            else -> { // default: month
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -1)
                cal.timeInMillis
            }
        }

        val expenses = repo.listExpenses().filter { it.date >= cutoff }
        val total = expenses.sumOf { it.amount }
        val byCat = expenses.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
        val topItems = expenses.sortedByDescending { it.amount }.take(5)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return JSONObject().apply {
            put("period", period)
            put("total", "%.2f".format(total))
            put("count", expenses.size)
            put("byCategory", JSONArray().apply {
                byCat.forEach { (cat, sum) ->
                    put(JSONObject().apply { put("category", cat); put("total", "%.2f".format(sum)) })
                }
            })
            put("topItems", JSONArray().apply {
                topItems.forEach { e ->
                    put(JSONObject().apply {
                        put("description", e.description.ifEmpty { e.category })
                        put("amount", "%.2f".format(e.amount))
                        put("date", dateFmt.format(Date(e.date)))
                        put("category", e.category)
                    })
                }
            })
        }.toString()
    }

    // ───────── Document Q&A ("Ask My Documents") — Phase 0 ─────────
    //
    // Phase 0 is intentionally simple: load the OCR text from the vault
    // sidecar, clip to fit Gemma's context, and hand the whole thing to
    // the model. No retrieval, no chunking, no embeddings. The whole
    // point of this spike is to test whether on-device LLM + scanned
    // document text produces good-enough answers before committing to
    // the full RAG infrastructure (Phase 1).

    /** Hard cap on OCR text fed to the model. ~6 KB ≈ 1.5 K tokens; leaves
     *  room for prompt + answer in Gemma 4 E2B's effective window. */
    private const val DOC_TEXT_BUDGET = 6144

    /**
     * Locate a document by id (the encrypted-file basename, e.g.
     * `scan_1714944000000.pdf`) anywhere in the vault — categories first,
     * then custom folders. Returns null if not found.
     */
    private fun findDocument(vault: VaultRepository, id: String): com.privateai.camera.security.VaultPhoto? {
        // Most scanned docs land in the SCAN category; check there first for speed.
        VaultCategory.entries.firstOrNull { cat ->
            vault.listPhotos(cat).any { it.id == id }
        }?.let { cat ->
            return vault.listPhotos(cat).firstOrNull { it.id == id }
        }
        return null
    }

    /**
     * Return a JSON payload describing a vault document for the assistant
     * to summarize. Includes the OCR text (clipped) plus a flag if it was
     * truncated, so Gemma can hedge appropriately.
     */
    fun summarizeDocument(vault: VaultRepository, docId: String): String {
        val doc = findDocument(vault, docId)
            ?: return """{"error":"Document not found in vault."}"""
        val text = vault.loadOcr(doc)
            ?: return """{"error":"This document doesn't have searchable text. Re-scan it through the Privora scanner to enable Q&A."}"""
        val clipped = text.length > DOC_TEXT_BUDGET
        val payload = if (clipped) text.take(DOC_TEXT_BUDGET) else text
        return JSONObject().apply {
            put("docId", doc.id)
            put("text", payload)
            put("truncated", clipped)
            put("originalLength", text.length)
            put("mediaType", doc.mediaType.name)
        }.toString()
    }

    /**
     * Return a JSON payload for an "ask a question" turn. The query string
     * carries the doc id and the user's question, separated by a `|` —
     * matches the existing single-string tool param convention rather than
     * adding a second field to ParsedReply.
     *
     * Format: `<docId>|<question>`
     */
    fun askDocument(vault: VaultRepository, query: String): String {
        val sep = query.indexOf('|')
        if (sep < 0) {
            return """{"error":"ask_document needs '<docId>|<question>'."}"""
        }
        val docId = query.substring(0, sep).trim()
        val question = query.substring(sep + 1).trim()
        if (docId.isEmpty() || question.isEmpty()) {
            return """{"error":"ask_document needs both a docId and a question."}"""
        }
        val doc = findDocument(vault, docId)
            ?: return """{"error":"Document not found in vault."}"""
        val text = vault.loadOcr(doc)
            ?: return """{"error":"This document doesn't have searchable text. Re-scan it through the Privora scanner to enable Q&A."}"""
        val clipped = text.length > DOC_TEXT_BUDGET
        val payload = if (clipped) text.take(DOC_TEXT_BUDGET) else text
        return JSONObject().apply {
            put("docId", doc.id)
            put("question", question)
            put("text", payload)
            put("truncated", clipped)
            put("originalLength", text.length)
        }.toString()
    }
}
