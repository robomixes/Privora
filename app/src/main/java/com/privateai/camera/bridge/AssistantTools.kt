package com.privateai.camera.bridge

import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.SecureNote
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Parsed reply from the assistant model. Either a final text answer
 * or a tool-call request that the host must execute and feed back.
 */
sealed class ParsedReply {
    data class Answer(val text: String) : ParsedReply()
    /** A tool call. [query] is the primary param — doubles as query/id/period depending on the tool. */
    data class ToolCall(val name: String, val query: String) : ParsedReply()

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
}
