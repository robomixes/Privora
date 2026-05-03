// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.privateai.camera.MainActivity
import com.privateai.camera.R

/**
 * Fired when an AlarmManager alarm triggers for a scheduled reminder.
 * Posts a notification with Done / Skip actions and re-schedules the next occurrence.
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "privora_reminders"
        private const val TAG = "ReminderReceiver"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Medication and schedule reminders"
                    enableLights(true)
                    enableVibration(true)
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(ReminderScheduler.EXTRA_SCHEDULE_ID) ?: return
        val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Reminder"
        Log.i(TAG, "Fire: $scheduleId @ $time — $title")

        createNotificationChannel(context)
        postNotification(context, scheduleId, time, title)

        // Re-schedule next occurrence for recurring items. One-shot items fire once and stop.
        try {
            val crypto = com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
            if (crypto.isUnlocked()) {
                val repo = com.privateai.camera.security.InsightsRepository(
                    java.io.File(context.filesDir, "vault/insights"), crypto
                )
                val item = repo.listScheduleItems().find { it.id == scheduleId }
                if (item != null && item.enabled) {
                    if (item.isOneShot) {
                        // Disable so it doesn't accidentally fire again; keep record for history
                        repo.saveSchedule(item.copy(enabled = false))
                    } else {
                        ReminderScheduler.scheduleItem(context, item)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not re-schedule: ${e.message}")
        }
    }

    private fun postNotification(context: Context, scheduleId: String, time: String, title: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = ("$scheduleId@$time").hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(ReminderScheduler.EXTRA_TIME, time)
            putExtra("notif_id", notifId)
        }
        val skipIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SKIP
            putExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(ReminderScheduler.EXTRA_TIME, time)
            putExtra("notif_id", notifId)
        }
        val donePi = PendingIntent.getBroadcast(
            context, notifId * 10 + 1, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipPi = PendingIntent.getBroadcast(
            context, notifId * 10 + 2, skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Scheduled for $time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(0, "Done", donePi)
            .addAction(0, "Skip", skipPi)
            // Privacy: hide reminder content on the lock screen so passersby can't read it.
            // Full title + time are only visible when the device is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.reminder_notification_private))
                    .setContentText("")
                    .build()
            )
            .build()
        nm.notify(notifId, notification)
    }
}
