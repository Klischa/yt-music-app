package com.klischa.ytmusic.data.innertube

/**
 * InnerTube Context для авторизации запросов как клиент YouTube Music (WEB_REMIX).
 */
data class InnerTubeContext(
    val client: ClientInfo = ClientInfo()
) {
    data class ClientInfo(
        val clientName: String = "WEB_REMIX",
        val clientVersion: String = "1.20240401.01.00",
        val hl: String = "ru",
        val gl: String = "RU",
        val osName: String = "Windows",
        val osVersion: String = "10.0",
        val platform: String = "DESKTOP"
    )
}

data class InnerTubeSearchRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val query: String
)

data class InnerTubePlayerRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val videoId: String,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true
)

data class FormatItem(
    val itag: Int?,
    val url: String?,
    val mimeType: String?,
    val bitrate: Int?,
    val averageBitrate: Int?,
    val contentLength: String?,
    val signatureCipher: String?,
    val cipher: String?,
    val audioQuality: String?,
    val approxDurationMs: String?
)
