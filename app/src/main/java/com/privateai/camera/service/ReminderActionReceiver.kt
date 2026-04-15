package com.privateai.camera.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.HabitLog
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.LogState
import com.privateai.camera.security.ScheduleKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles taps on Done / Skip action buttons inside the reminder notification.
 * Writes a ScheduleLog entry with DONE or SKIPPED, then dismisses the notification.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE = "com.privateai.camera.REMINDER_DONE"
        const val ACTION_SKIP = "com.privateai.camera.REMINDER_SKIP"
        private const val TAG = "ReminderAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(ReminderScheduler.EXTRA_SCHEDULE_ID) ?: return
        val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return
        val notifId = intent.getIntExtra("notif_id", -1)
        val state = when (intent.action) {
            ACTION_DONE -> LogState.DONE
            ACTION_SKIP -> LogState.SKIPPED
            else -> return
        }

        try {
            val crypto = CryptoManager(context).also { it.initialize() }
            if (crypto.isUnlocked()) {
                val repo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                // Normalize one-shot time slot ("ONESHOT") to actual HH:mm so the in-app UI can match it.
                // The alarm scheduler uses "ONESHOT" as a stable PendingIntent key, but the today
                // list renders one-shots with HH:mm derived from oneShotAt — without normalizing
                // here, the log entry would never light up the Done/Skipped badge in-app.
                val item = repo.loadScheduleItem(scheduleId)
                val normalizedTime = if (time == "ONESHOT" && item != null && item.isOneShot && item.oneShotAt != null) {
                    SimpleDateFormat("HH:mm", Locale.US).format(Date(item.oneShotAt))
                } else time
                repo.markScheduleEntry(today, scheduleId, normalizedTime, state)
                Log.i(TAG, "$state for $scheduleId @ $normalizedTime")

                // Phase F.2: Propagate Done to source. When the reminder is linked to a Habit
                // (kind=HABIT, sourceId set), also tick that habit in today's HabitLog so the
                // user sees their checklist update without having to open Habits separately.
                // Medication "last taken" is already derivable from the ScheduleLog DONE entry,
                // so no extra mirror needed for kind=MEDICATION.
                if (state == LogState.DONE && item != null && item.kind == ScheduleKind.HABIT && item.sourceId != null) {
                    val log = repo.loadHabitLog(today)
                    if (item.sourceId !in log.completed) {
                        repo.saveHabitLog(HabitLog(today, log.completed + item.sourceId))
                        Log.i(TAG, "Auto-ticked habit ${item.sourceId} from reminder")
                    }
                }
            } else {
                Log.w(TAG, "Crypto locked — cannot write log now; missing mark")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark: ${e.message}")
        }

        // Dismiss the notification
        if (notifId >= 0) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)
        }
    }
}
