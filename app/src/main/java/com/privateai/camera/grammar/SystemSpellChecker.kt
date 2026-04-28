// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.grammar

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Wraps Android's built-in SpellCheckerService for on-device spell checking.
 * Uses whatever spell checker the user has installed (Google, Samsung, etc.).
 * Fully on-device — no network calls.
 */
class SystemSpellChecker(private val context: Context) : GrammarChecker {

    override suspend fun check(text: String): List<GrammarError> {
        if (text.isBlank()) return emptyList()

        return withTimeoutOrNull(3000) {
            withContext(Dispatchers.Main) {
                val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
                    ?: return@withContext emptyList()
                try {
                    chunks = splitIntoChunks(text, 200)
                    checkWithService(tsm, text)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        } ?: emptyList()
    }

    private suspend fun checkWithService(
        tsm: TextServicesManager,
        text: String
    ): List<GrammarError> = suspendCancellableCoroutine { cont ->
        val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
            override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
                // Not used — we use sentence-level API
            }

            override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                val errors = mutableListOf<GrammarError>()
                results?.forEachIndexed { chunkIdx, sentenceInfo ->
                    val chunkOffset = chunks.getOrNull(chunkIdx)?.first ?: 0
                    for (i in 0 until sentenceInfo.suggestionsCount) {
                        val info = sentenceInfo.getSuggestionsInfoAt(i)
                        val localOffset = sentenceInfo.getOffsetAt(i)
                        val length = sentenceInfo.getLengthAt(i)
                        val attrs = info.suggestionsAttributes

                        // Compute absolute offset in the original text
                        val absOffset = chunkOffset + localOffset

                        if (attrs and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0 ||
                            attrs and SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS != 0
                        ) {
                            val suggestions = mutableListOf<String>()
                            for (j in 0 until info.suggestionsCount) {
                                suggestions.add(info.getSuggestionAt(j))
                            }
                            if (suggestions.isNotEmpty() && absOffset + length <= text.length) {
                                errors.add(
                                    GrammarError(
                                        fromPos = absOffset,
                                        toPos = absOffset + length,
                                        message = "Possible spelling error",
                                        suggestions = suggestions.take(3),
                                        ruleId = "SPELL_CHECK"
                                    )
                                )
                            }
                        }
                    }
                }
                if (cont.isActive) cont.resume(errors)
            }
        }

        val session = tsm.newSpellCheckerSession(null, null, listener, false)
        if (session == null) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        cont.invokeOnCancellation {
            session.close()
        }

        val textInfos = chunks.map { (_, chunk) -> TextInfo(chunk) }.toTypedArray()

        if (textInfos.isNotEmpty()) {
            session.getSentenceSuggestions(textInfos, 3)
        } else {
            cont.resume(emptyList())
            session.close()
        }
    }

    // Stored so the callback can access chunk offsets
    private var chunks: List<Pair<Int, String>> = emptyList()

    private fun splitIntoChunks(text: String, maxLen: Int): List<Pair<Int, String>> {
        if (text.length <= maxLen) return listOf(0 to text)
        val result = mutableListOf<Pair<Int, String>>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxLen, text.length)
            if (end < text.length) {
                val spaceIdx = text.lastIndexOf(' ', end)
                if (spaceIdx > start) end = spaceIdx
            }
            result.add(start to text.substring(start, end))
            start = end
            while (start < text.length && text[start] == ' ') start++
        }
        return result
    }
}
