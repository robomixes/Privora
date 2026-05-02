// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the Gemma 4 model download via the system [DownloadManager].
 *
 * Why DownloadManager and not a foreground service:
 *  - No FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC permission required.
 *    Play Console doesn't ask for a video justifying the foreground service.
 *  - The system shows the download notification automatically with the same
 *    look as a Chrome / Drive download.
 *  - Pause/resume on Wi-Fi disconnect, retries, and backgrounding are handled
 *    by the framework — no custom WakeLock or foreground notification code.
 *
 * The download lands in external app-private storage
 * ([Context.getExternalFilesDir]). Same sandbox semantics as `filesDir`: other
 * apps can't read it, wiped on uninstall. Required because DownloadManager
 * can't write directly into `/data/data/<pkg>/files/`.
 *
 * UI binds to [downloadState] for live progress; we poll DownloadManager every
 * second while a download is active and emit a sealed state to the StateFlow.
 */
object GemmaModelManager {

    private const val TAG = "GemmaModelManager"
    private const val PREFS = "gemma_settings"
    private const val KEY_DOWNLOAD_ID = "dm_download_id"

    private const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progressBytes: Long, val totalBytes: Long) : DownloadState()
        data object Complete : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var receiverRegistered = false

    /**
     * Kick off the download. Idempotent — if a download is already in flight
     * (id stored in prefs), we resume polling it instead of enqueuing a second.
     */
    fun startDownload(context: Context) {
        val app = context.applicationContext
        ensureReceiver(app)

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existingId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        if (existingId != -1L && isDownloadActive(dm, existingId)) {
            Log.i(TAG, "Resuming progress polling for existing download id=$existingId")
            startPolling(app, existingId)
            return
        }

        // Make sure the destination dir exists.
        val targetFile = GemmaRunner.getModelFile(app)
        targetFile.parentFile?.mkdirs()
        // DownloadManager refuses to overwrite — clean any partial.
        if (targetFile.exists()) targetFile.delete()

        val request = DownloadManager.Request(Uri.parse(MODEL_URL)).apply {
            setTitle(app.getString(com.privateai.camera.R.string.gemma_download_title))
            setDescription(app.getString(com.privateai.camera.R.string.gemma_download_desc))
            setDestinationUri(Uri.fromFile(targetFile))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(false)   // Wi-Fi only by default — 2.5 GB on cellular is rough
            setAllowedOverRoaming(false)
            // Keep it out of the public Downloads UI scan; this isn't a user file.
            @Suppress("DEPRECATION")
            setVisibleInDownloadsUi(false)
        }

        val id = dm.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        _downloadState.value = DownloadState.Downloading(0, 0)
        Log.i(TAG, "Enqueued model download id=$id")
        startPolling(app, id)
    }

    /** Cancel any in-flight download and clear stored state. */
    fun cancelDownload(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id != -1L) {
            (app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        }
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        pollJob?.cancel()
        pollJob = null
        _downloadState.value = DownloadState.Idle
    }

    /** Resume polling on app start if a download was in flight when the app last died. */
    fun reconnect(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id == -1L) return
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        if (isDownloadActive(dm, id)) {
            ensureReceiver(app)
            startPolling(app, id)
        } else {
            // Either it finished while the app was dead, or DownloadManager forgot it.
            // Sync state from disk: if the file's there, we're complete.
            if (GemmaRunner.isModelDownloaded(app)) {
                _downloadState.value = DownloadState.Complete
            }
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        }
    }

    private fun startPolling(context: Context, id: Long) {
        pollJob?.cancel()
        pollJob = scope.launch {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            while (true) {
                val info = queryDownload(dm, id)
                if (info == null) {
                    _downloadState.value = DownloadState.Idle
                    break
                }
                when (info.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_RUNNING -> {
                        _downloadState.value = DownloadState.Downloading(info.downloaded, info.total)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _downloadState.value = DownloadState.Complete
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().remove(KEY_DOWNLOAD_ID).apply()
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        _downloadState.value = DownloadState.Error("Download failed (reason ${info.reason})")
                        // Disable AI so the user can retry from Settings instead of being stuck.
                        GemmaRunner.setEnabled(context, false)
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().remove(KEY_DOWNLOAD_ID).apply()
                        break
                    }
                }
                delay(1_000)
            }
        }
    }

    private data class DownloadInfo(val status: Int, val downloaded: Long, val total: Long, val reason: Int)

    private fun queryDownload(dm: DownloadManager, id: Long): DownloadInfo? {
        val q = DownloadManager.Query().setFilterById(id)
        var cursor: Cursor? = null
        return try {
            cursor = dm.query(q)
            if (cursor == null || !cursor.moveToFirst()) return null
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val sizeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val doneIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            DownloadInfo(
                status = cursor.getInt(statusIdx),
                downloaded = cursor.getLong(doneIdx),
                total = cursor.getLong(sizeIdx).coerceAtLeast(0),
                reason = cursor.getInt(reasonIdx)
            )
        } catch (e: Exception) {
            Log.w(TAG, "queryDownload failed: ${e.message}")
            null
        } finally {
            cursor?.close()
        }
    }

    private fun isDownloadActive(dm: DownloadManager, id: Long): Boolean {
        val info = queryDownload(dm, id) ?: return false
        return info.status == DownloadManager.STATUS_PENDING ||
            info.status == DownloadManager.STATUS_RUNNING ||
            info.status == DownloadManager.STATUS_PAUSED
    }

    /**
     * Listen for ACTION_DOWNLOAD_COMPLETE so we update state immediately when
     * the download finishes (without waiting for the next poll tick).
     */
    @Synchronized
    private fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val ours = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getLong(KEY_DOWNLOAD_ID, -1L)
                if (id == -1L || id != ours) return
                val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val info = queryDownload(dm, id)
                if (info == null) return
                if (info.status == DownloadManager.STATUS_SUCCESSFUL) {
                    _downloadState.value = DownloadState.Complete
                } else if (info.status == DownloadManager.STATUS_FAILED) {
                    _downloadState.value = DownloadState.Error("Download failed (reason ${info.reason})")
                    GemmaRunner.setEnabled(app, false)
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_DOWNLOAD_ID).apply()
                pollJob?.cancel()
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }
}
