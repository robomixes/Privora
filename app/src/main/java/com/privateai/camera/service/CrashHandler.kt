// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.service

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught exception handler.
 * Logs crashes to a local file — never sent anywhere.
 * Cleans up temp files before letting Android handle the crash.
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val LOG_DIR = "crash_logs"
        private const val MAX_LOGS = 10

        fun install(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, defaultHandler))
            Log.i(TAG, "Crash handler installed")
        }

        /**
         * Get the crash log directory.
         */
        fun getLogDir(context: Context): File {
            return File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        }

        /**
         * List crash logs, newest first.
         */
        fun listLogs(context: Context): List<CrashLog> {
            val dir = getLogDir(context)
            return (dir.listFiles() ?: emptyArray())
                .filter { it.name.endsWith(".log") }
                .sortedByDescending { it.lastModified() }
                .map { file ->
                    CrashLog(
                        file = file,
                        timestamp = file.lastModified(),
                        preview = file.readLines().take(3).joinToString("\n")
                    )
                }
        }

        /**
         * Read full crash log content.
         */
        fun readLog(file: File): String {
            return try { file.readText() } catch (_: Exception) { "Could not read log" }
        }

        /**
         * Delete all crash logs.
         */
        fun clearLogs(context: Context) {
            getLogDir(context).listFiles()?.forEach { it.delete() }
        }

        /**
         * Delete a single log.
         */
        fun deleteLog(file: File) {
            file.delete()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 1. Write crash log to local file
            writeCrashLog(throwable)

            // 2. Clean up temp files (recordings, playback, cache shares)
            cleanupTempFiles()

        } catch (_: Exception) {
            // Don't let our handler crash
        } finally {
            // 3. Let Android handle the crash (show dialog, kill process)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(throwable: Throwable) {
        val dir = getLogDir(context)

        // Prune old logs
        val existing = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (existing.size >= MAX_LOGS) {
            existing.drop(MAX_LOGS - 1).forEach { it.delete() }
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val logFile = File(dir, "crash_$timestamp.log")

        val stackTrace = StringWriter().also { sw ->
            throwable.printStackTrace(PrintWriter(sw))
        }.toString()

        val content = buildString {
            appendLine("=== Privora Crash Report ===")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("App: ${context.packageName} v${getAppVersion()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${Thread.currentThread().name}")
            appendLine()
            appendLine("=== Exception ===")
            appendLine("${throwable.javaClass.simpleName}: ${throwable.message}")
            appendLine()
            appendLine("=== Stack Trace ===")
            append(stackTrace)
        }

        logFile.writeText(content)
        Log.e(TAG, "Crash log saved: ${logFile.name}")
    }

    private fun cleanupTempFiles() {
        try {
            // Clean up recording temp files
            context.cacheDir.listFiles()?.filter {
                it.name.startsWith("rec_") ||
                it.name.startsWith("playback_") ||
                it.name.startsWith("import_backup") ||
                it.name.endsWith(".tmp")
            }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }
}

data class CrashLog(
    val file: File,
    val timestamp: Long,
    val preview: String
)
