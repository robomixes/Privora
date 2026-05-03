// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.grammar

/**
 * Represents a grammar or spelling error found in text.
 */
data class GrammarError(
    val fromPos: Int,
    val toPos: Int,
    val message: String,
    val suggestions: List<String>,
    val ruleId: String
)

/**
 * Interface for grammar/spell checking engines.
 * All implementations must run fully on-device with no network calls.
 */
interface GrammarChecker {
    suspend fun check(text: String): List<GrammarError>
}
