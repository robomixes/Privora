// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.privateai.camera.MainActivity
import com.privateai.camera.R
import com.privateai.camera.bridge.GemmaModelManager
import com.privateai.camera.bridge.GemmaRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground service that downloads the Gemma 4 model.
 * Survives navigation, screen off, and app switching.
 * Shows persistent notification with progress.
 */
class GemmaDownloadService : Service() {

    companion object {
        private const val TAG = "GemmaDownload"
        const val CHANNEL_ID = "gemma_download"
        const val NOTIFICATION_ID = 9001
        const val ACTION_CANCEL = "com.privateai.camera.CANCEL_GEMMA_DOWNLOAD"

        private const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        private const val EXPECTED_SIZE_BYTES = 2_770_000_000L

        fun start(context: Context) {
            val intent = Intent(context, GemmaDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.stopService(Intent(context, GemmaDownloadService::class.java))
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "AI Model Download",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress while downloading the AI model"
                    setShowBadge(false)
                }
                (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)

        // Acquire wake lock to keep CPU running during download
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "privora:gemma_download")
        wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(0, 0))

        downloadJob = scope.launch {
            doDownload()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        GemmaModelManager.setDownloadState(GemmaModelManager.DownloadState.Idle)
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun doDownload() {
        val modelFile = GemmaRunner.getModelFile(this)
        val modelDir = modelFile.parentFile!!
        if (!modelDir.exists()) modelDir.mkdirs()

        val tempFile = File(modelDir, "${modelFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        GemmaModelManager.setDownloadState(GemmaModelManager.DownloadState.Downloading(0, EXPECTED_SIZE_BYTES))

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                GemmaModelManager.setDownloadState(
                    GemmaModelManager.DownloadState.Error("Download failed (HTTP $responseCode)")
                )
                GemmaRunner.setEnabled(this@GemmaDownloadService, false)
                return
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (contentLength > 0) contentLength else EXPECTED_SIZE_BYTES

            val buffer = ByteArray(256 * 1024)
            var downloaded = 0L
            var bytesRead: Int
            var lastNotificationUpdate = 0L

            tempFile.outputStream().use { out ->
                connection.inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        GemmaModelManager.setDownloadState(
                            GemmaModelManager.DownloadState.Downloading(downloaded, totalBytes)
                        )

                        // Update notification at most once per second
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationUpdate > 1000) {
                            updateNotification(downloaded, totalBytes)
                            lastNotificationUpdate = now
                        }
                    }
                }
            }

            // Rename temp to final
            tempFile.renameTo(modelFile)
            GemmaModelManager.setDownloadState(GemmaModelManager.DownloadState.Complete)
            showCompleteNotification()
            Log.i(TAG, "Model download complete ($downloaded bytes)")

        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            GemmaModelManager.setDownloadState(
                GemmaModelManager.DownloadState.Error(e.message ?: "Unknown error")
            )
            GemmaRunner.setEnabled(this, false)
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildNotification(downloaded: Long, total: Long): Notification {
        val pct = if (total > 0) ((downloaded.toFloat() / total) * 100).toInt() else 0
        val cancelIntent = Intent(this, GemmaDownloadService::class.java).apply { action = ACTION_CANCEL }
        val cancelPending = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE)

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Downloading AI Model")
            .setContentText("$pct% — ${formatSize(downloaded)} / ${formatSize(total)}")
            .setProgress(100, pct, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, "Cancel", cancelPending)
            .build()
    }

    private fun updateNotification(downloaded: Long, total: Long) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(downloaded, total))
    }

    private fun showCompleteNotification() {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AI Model Ready")
            .setContentText("Gemma 4 downloaded successfully. AI features are now available.")
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
