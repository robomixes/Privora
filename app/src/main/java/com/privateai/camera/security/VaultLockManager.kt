// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.content.Context

/**
 * Manages vault/notes unlock state with grace period.
 * Singleton — survives navigation between screens within the app.
 */
object VaultLockManager {

    private var lastUnlockTime: Long = 0L
    private var isCurrentlyUnlocked: Boolean = false

    /** True when the current session was unlocked with an emergency/duress PIN. */
    var isDuressActive: Boolean = false
        private set

    fun activateDuress() {
        isDuressActive = true
    }

    /**
     * Mark vault as unlocked now.
     */
    fun markUnlocked() {
        isCurrentlyUnlocked = true
        lastUnlockTime = System.currentTimeMillis()
    }

    /**
     * Mark vault as left (user navigated away or app backgrounded).
     */
    fun markLeft() {
        if (isCurrentlyUnlocked) {
            lastUnlockTime = System.currentTimeMillis()
        }
    }

    /**
     * Clear duress state (e.g. when normal PIN is entered after emergency PIN).
     */
    fun clearDuress() {
        isDuressActive = false
    }

    /**
     * Force lock (e.g. grace period expired).
     */
    fun lock() {
        isCurrentlyUnlocked = false
        lastUnlockTime = 0L
        isDuressActive = false
    }

    /**
     * Check if vault should still be unlocked (within grace period).
     */
    fun isUnlockedWithinGrace(context: Context): Boolean {
        if (!isCurrentlyUnlocked) return false
        if (lastUnlockTime == 0L) return false

        val gracePeriodMs = context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
            .getInt("lock_grace_seconds", 30) * 1000L

        val elapsed = System.currentTimeMillis() - lastUnlockTime
        return elapsed <= gracePeriodMs
    }
}
