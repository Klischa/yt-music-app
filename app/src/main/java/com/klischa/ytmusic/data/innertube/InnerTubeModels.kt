package com.klischa.ytmusic.data.innertube

import com.google.gson.annotations.SerializedName

/**
 * InnerTube Context для авторизации запросов как официальный клиент YouTube Music.
 */
data class InnerTubeContext(
    val client: ClientInfo = ClientInfo()
) {
    data class ClientInfo(
        val clientName: String = "ANDROID_MUSIC",
        val clientVersion: String = "6.42.52",
        val hl: String = "ru",
        val gl: String = "RU",
        val androidSdkVersion: Int = 34,
        val osName: String = "Android",
        val osVersion: String = "14"
    )
}

data class InnerTubeSearchRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val query: String,
    val params: String? = "Eg-KAQwIARAAGAAgACgAMABqChAKEAMQBBAKEAU%3D" // Фильтр "Песни"
)

data class InnerTubePlayerRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val videoId: String,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true
)

// Response Models
data class InnerTubeSearchResponse(
    val contents: ContentsContainer?
)

data class ContentsContainer(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer?,
    val sectionListRenderer: SectionListRenderer?
)

data class TabbedSearchResultsRenderer(
    val tabs: List<TabItem>?
)

data class TabItem(
    val tabRenderer: TabRenderer?
)

data class TabRenderer(
    val content: SectionListRenderer?
)

data class SectionListRenderer(
    val contents: List<SectionItem>?
)

data class SectionItem(
    val musicShelfRenderer: MusicShelfRenderer?,
    val itemSectionRenderer: ItemSectionRenderer?
)

data class ItemSectionRenderer(
    val contents: List<ShelfContentItem>?
)

data class MusicShelfRenderer(
    val contents: List<ShelfContentItem>?
)

data class ShelfContentItem(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
    val compactVideoRenderer: CompactVideoRenderer?
)

data class CompactVideoRenderer(
    val videoId: String?,
    val title: TextRuns?,
    val longBylineText: TextRuns?,
    val lengthText: TextRuns?,
    val thumbnail: ThumbnailContainer?
)

data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>?,
    val thumbnail: ThumbnailContainer?,
    val playlistItemData: PlaylistItemData?
)

data class PlaylistItemData(
    val videoId: String?
)

data class FlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer?
)

data class FlexColumnRenderer(
    val text: TextRuns?
)

data class TextRuns(
    val runs: List<RunItem>?
)

data class RunItem(
    val text: String?,
    val navigationEndpoint: NavigationEndpoint?
)

data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint?
)

data class WatchEndpoint(
    val videoId: String?
)

data class ThumbnailContainer(
    val thumbnails: List<ThumbnailItem>?,
    val musicThumbnailRenderer: MusicThumbnailRenderer?
)

data class MusicThumbnailRenderer(
    val thumbnail: ThumbnailsList?
)

data class ThumbnailsList(
    val thumbnails: List<ThumbnailItem>?
)

data class ThumbnailItem(
    val url: String?,
    val width: Int?,
    val height: Int?
)

// Player Response
data class InnerTubePlayerResponse(
    val videoDetails: VideoDetails?,
    val streamingData: StreamingData?,
    val playabilityStatus: PlayabilityStatus?
)

data class PlayabilityStatus(
    val status: String?,
    val reason: String?
)

data class VideoDetails(
    val videoId: String?,
    val title: String?,
    val lengthSeconds: String?,
    val author: String?,
    val channelId: String?,
    val thumbnail: ThumbnailContainer?
)

data class StreamingData(
    val expiresInSeconds: String?,
    val formats: List<FormatItem>?,
    val adaptiveFormats: List<FormatItem>?
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
