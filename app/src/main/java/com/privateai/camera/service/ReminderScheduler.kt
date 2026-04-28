// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.privateai.camera.security.ScheduleItem
import java.util.Calendar

/**
 * Wraps Android AlarmManager for reminder scheduling.
 *
 * For each enabled ScheduleItem, schedules the next fire per time-of-day × days-of-week.
 * Uses setExactAndAllowWhileIdle to fire during Doze.
 * Each alarm has a unique requestCode derived from item.id + time-of-day so multiple times
 * on the same item are independent alarms.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_TIME = "time"
    const val EXTRA_TITLE = "title"

    /** Returns true if the app can schedule exact alarms (needed for reliable on-time firing). */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** Open the system settings screen where the user can grant SCHEDULE_EXACT_ALARM. */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { Log.e(TAG, "Could not open exact alarm settings: ${e.message}") }
    }

    /** Schedule alarms for this item's next fire(s). Cancels any prior ones first. */
    fun scheduleItem(context: Context, item: ScheduleItem) {
        cancelItem(context, item.id, item.timesOfDay)
        if (!item.enabled) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

        // One-shot mode: single alarm at the specified moment, no re-scheduling
        if (item.isOneShot) {
            val fireAt = item.oneShotAt ?: return
            if (fireAt <= System.currentTimeMillis()) {
                Log.w(TAG, "One-shot time already in the past, skipping '${item.title}'")
                return
            }
            // Use "ONESHOT" as the time-slot key so cancel/fire PendingIntent keys are stable
            val pi = buildPendingIntent(context, item.id, "ONESHOT", item.title, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            try {
                if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                Log.i(TAG, "Scheduled one-shot '${item.title}' → ${Calendar.getInstance().apply { timeInMillis = fireAt }.time}")
            } catch (se: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            }
            return
        }

        // Recurring mode: one alarm per time-of-day × day-of-week
        if (item.timesOfDay.isEmpty()) return
        item.timesOfDay.forEach { time ->
            val next = nextFireTime(time, item.daysOfWeek) ?: return@forEach
            val pi = buildPendingIntent(context, item.id, time, item.title, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEach
            try {
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                }
                Log.i(TAG, "Scheduled '${item.title}' @ $time → next fire ${Calendar.getInstance().apply { timeInMillis = next }.time}")
            } catch (se: SecurityException) {
                Log.e(TAG, "Failed to schedule exact alarm: ${se.message}")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        }
    }

    /** Cancel all alarms for a given ScheduleItem id. */
    fun cancelItem(context: Context, itemId: String, timesOfDay: List<String> = emptyList()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timesToCancel = if (timesOfDay.isNotEmpty()) timesOfDay else (0..23).map { "%02d:00".format(it) }
        // Also cancel the one-shot slot (if any)
        (timesToCancel + "ONESHOT").forEach { time ->
            val pi = buildPendingIntent(context, itemId, time, "", PendingIntent.FLAG_NO_CREATE)
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    /** Re-schedule all enabled items (called from BootReceiver and when AlarmManager is initialised). */
    fun rescheduleAll(context: Context, items: List<ScheduleItem>) {
        items.forEach { scheduleItem(context, it) }
    }

    private fun buildPendingIntent(
        context: Context,
        scheduleId: String,
        time: String,
        title: String,
        extraFlag: Int
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.privateai.camera.REMINDER_FIRE"
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_TITLE, title)
        }
        val requestCode = requestCodeFor(scheduleId, time)
        val flags = extraFlag or PendingIntent.FLAG_IMMUTABLE
        return try {
            PendingIntent.getBroadcast(context, requestCode, intent, flags)
        } catch (_: Exception) { null }
    }

    /** Next fire time for `time` (HH:mm) on any allowed weekday, as UTC millis. */
    private fun nextFireTime(time: String, daysOfWeek: Set<Int>): Long? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1) // tomorrow same time

        // Walk forward until we hit an allowed weekday (or any if set is empty)
        repeat(8) {
            if (daysOfWeek.isEmpty() || toIsoWeekday(cal.get(Calendar.DAY_OF_WEEK)) in daysOfWeek) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    private fun toIsoWeekday(javaDow: Int): Int = when (javaDow) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
        else -> 7 // Sunday
    }

    private fun requestCodeFor(scheduleId: String, time: String): Int {
        // Stable hash so cancel finds the same PendingIntent
        return ("$scheduleId@$time").hashCode()
    }
}
