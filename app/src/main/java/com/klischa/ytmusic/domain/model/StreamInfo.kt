package com.klischa.ytmusic.domain.model

data class StreamInfo(
    val videoId: String,
    val audioUrl: String,
    val bitrate: Int,
    val mimeType: String,
    val contentLength: Long,
    val expiresInSeconds: Long = 21600
)
