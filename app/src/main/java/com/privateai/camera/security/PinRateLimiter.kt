package com.privateai.camera.security

import android.content.Context

/**
 * Rate limiter for PIN entry attempts.
 * Escalating cooldowns after repeated failures, persisted across app restarts.
 *
 * Tiers:
 *   1-3 fails: no delay
 *   4-5 fails: 30 seconds
 *   6-7 fails: 2 minutes
 *   8-9 fails: 5 minutes
 *   10+ fails: 15 minutes
 */
object PinRateLimiter {

    private const val PREFS_NAME = "pin_rate_limiter"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"

    private val COOLDOWN_TIERS = listOf(
        4 to 30_000L,
        6 to 120_000L,
        8 to 300_000L,
        10 to 900_000L
    )

    fun canAttempt(context: Context): Boolean {
        return System.currentTimeMillis() >= getLockoutUntil(context)
    }

    fun remainingLockoutMs(context: Context): Long {
        val remaining = getLockoutUntil(context) - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    fun getFailedAttempts(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun recordFailure(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val lockoutDuration = getLockoutDuration(attempts)
        val lockoutUntil = if (lockoutDuration > 0) {
            System.currentTimeMillis() + lockoutDuration
        } else {
            0L
        }
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
            .apply()
    }

    fun recordSuccess(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    private fun getLockoutUntil(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LOCKOUT_UNTIL, 0L)
    }

    private fun getLockoutDuration(attempts: Int): Long {
        for ((threshold, duration) in COOLDOWN_TIERS.reversed()) {
            if (attempts >= threshold) return duration
        }
        return 0L
    }
}
