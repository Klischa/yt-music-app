package com.klischa.ytmusic.domain.model

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Доменная модель музыкального трека.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String = "",
    val streamUrl: String? = null,
    val isDownloaded: Boolean = false,
    val localUri: Uri? = null
) {
    val durationFormatted: String
        get() {
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            return String.format("%02d:%02d", mins, secs)
        }

    fun toMediaItem(): MediaItem {
        val uri = localUri ?: (streamUrl?.let { Uri.parse(it) } ?: Uri.EMPTY)
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(if (thumbnailUrl.isNotEmpty()) Uri.parse(thumbnailUrl) else null)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(uri)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(metadata)
            .build()
    }
}
