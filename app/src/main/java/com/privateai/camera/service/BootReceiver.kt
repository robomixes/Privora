package com.privateai.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.InsightsRepository
import java.io.File

/**
 * On device reboot, re-register all enabled schedule reminders with AlarmManager.
 *
 * Note: only runs if the vault is unlocked (CryptoManager can derive the DEK on boot).
 * If the user requires manual unlock, reminders will start firing once they enter the PIN
 * next time Insights is opened (we re-schedule from InsightsScreen on load as well).
 */
class BootReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "BootReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }
        Log.i(TAG, "BOOT_COMPLETED — re-scheduling reminders")
        try {
            val crypto = CryptoManager(context).also { it.initialize() }
            if (crypto.isUnlocked()) {
                val repo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val items = repo.listScheduleItems().filter { it.enabled }
                ReminderScheduler.rescheduleAll(context, items)
                Log.i(TAG, "Re-scheduled ${items.size} reminders")
            } else {
                Log.w(TAG, "Crypto locked at boot — reminders will be rescheduled on next app open")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Boot re-schedule failed: ${e.message}")
        }
    }
}
