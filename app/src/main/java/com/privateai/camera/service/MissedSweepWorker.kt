package com.privateai.camera.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.LogState
import com.privateai.camera.security.ScheduleLog
import com.privateai.camera.security.ScheduleLogEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that runs once a day. For each past day that has scheduled items
 * which haven't been marked DONE or SKIPPED, it writes a MISSED entry.
 *
 * Note: this is a best-effort sweep. The vault must be unlocked for it to run
 * (CryptoManager needs to derive DEK). If locked, it will re-run next day.
 */
class MissedSweepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MissedSweep"
        private const val UNIQUE_NAME = "reminder_missed_sweep"

        /** Schedule the daily sweep worker. Call this once at app start. */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<MissedSweepWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val crypto = CryptoManager(applicationContext).also { it.initialize() }
            if (!crypto.isUnlocked()) {
                Log.w(TAG, "Crypto locked — skipping sweep (will retry next day)")
                return Result.success()
            }
            val repo = InsightsRepository(File(applicationContext.filesDir, "vault/insights"), crypto)
            val items = repo.listScheduleItems().filter { it.enabled }
            if (items.isEmpty()) return Result.success()

            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = dateFmt.format(Date())

            // Walk back up to 7 days — any past day without DONE/SKIPPED for a scheduled occurrence gets MISSED
            val cal = Calendar.getInstance()
            var sweptCount = 0
            for (dayOffset in 1..7) {
                cal.time = Date()
                cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
                val date = dateFmt.format(cal.time)
                if (date == today) continue
                val weekday = toIsoWeekday(cal.get(Calendar.DAY_OF_WEEK))
                val expected = items.flatMap { item ->
                    if (item.daysOfWeek.isEmpty() || weekday in item.daysOfWeek) {
                        item.timesOfDay.map { time -> item.id to time }
                    } else emptyList()
                }
                if (expected.isEmpty()) continue

                val existingLog = repo.loadScheduleLog(date)
                val existingKeys = existingLog.entries.map { it.scheduleId to it.time }.toSet()
                val missing = expected.filter { it !in existingKeys }
                if (missing.isEmpty()) continue

                val newEntries = existingLog.entries + missing.map { (sid, t) ->
                    ScheduleLogEntry(sid, t, LogState.MISSED)
                }
                repo.saveScheduleLog(ScheduleLog(date, newEntries))
                sweptCount += missing.size
            }
            Log.i(TAG, "Missed sweep marked $sweptCount entries as MISSED")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sweep failed: ${e.message}")
            Result.success() // don't retry on transient errors — next scheduled run will catch up
        }
    }

    private fun toIsoWeekday(javaDow: Int): Int = when (javaDow) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
        else -> 7
    }
}
