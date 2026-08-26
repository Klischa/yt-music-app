package com.klischa.ytmusic.domain.model

import android.net.Uri

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val localUri: Uri, val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
