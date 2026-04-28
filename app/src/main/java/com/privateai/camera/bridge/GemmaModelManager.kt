// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.privateai.camera.service.GemmaDownloadService

/**
 * Shared state for Gemma 4 model download.
 * The actual download runs in GemmaDownloadService (foreground service).
 * This object exposes progress via StateFlow for UI binding.
 */
object GemmaModelManager {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progressBytes: Long, val totalBytes: Long) : DownloadState()
        data object Complete : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    /** Called by GemmaDownloadService to update progress. */
    fun setDownloadState(state: DownloadState) {
        _downloadState.value = state
    }

    /** Start the foreground download service. */
    fun startDownload(context: Context) {
        _downloadState.value = DownloadState.Downloading(0, 0)
        GemmaDownloadService.start(context)
    }

    /** Cancel the download service. */
    fun cancelDownload(context: Context) {
        GemmaDownloadService.cancel(context)
        _downloadState.value = DownloadState.Idle
    }
}
