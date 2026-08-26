package com.klischa.ytmusic.data.innertube

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.klischa.ytmusic.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WatchNextRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val tag = "WatchNextRepo"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class WatchNextResult(
        val upNextTracks: List<Track>,
        val lyricsBrowseId: String? = null,
        val relatedBrowseId: String? = null
    )

    suspend fun getWatchNext(videoId: String): WatchNextResult = withContext(Dispatchers.IO) {
        val url = "https://music.youtube.com/youtubei/v1/next"
        val payload = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20240401.01.00",
                        "hl": "ru",
                        "gl": "RU"
                    }
                },
                "videoId": "$videoId"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20240401.01.00")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext WatchNextResult(emptyList())

            val body = response.body?.string() ?: return@withContext WatchNextResult(emptyList())
            val root = JsonParser.parseString(body).asJsonObject

            val nextTracks = mutableListOf<Track>()
            var lyricsBrowseId: String? = null
            var relatedBrowseId: String? = null

            // 1. Поиск вкладок Текст / Похожие
            val tabs = root.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                ?.getAsJsonObject("tabbedRenderer")
                ?.getAsJsonObject("watchNextTabbedResultsRenderer")
                ?.getAsJsonArray("tabs")

            tabs?.forEach { tabElement ->
                if (tabElement.isJsonObject) {
                    val tabObj = tabElement.asJsonObject.getAsJsonObject("tabRenderer")
                    val title = tabObj?.get("title")?.asString ?: ""
                    val bId = tabObj?.getAsJsonObject("endpoint")?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString

                    if (title.contains("Текст", ignoreCase = true) || title.contains("Lyric", ignoreCase = true)) {
                        lyricsBrowseId = bId
                    } else if (title.contains("Похожие", ignoreCase = true) || title.contains("Related", ignoreCase = true)) {
                        relatedBrowseId = bId
                    }
                }
            }

            // 2. Поиск треков из очереди (Up Next)
            fun extractTracks(element: com.google.gson.JsonElement) {
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    if (obj.has("playlistPanelVideoRenderer")) {
                        val r = obj.getAsJsonObject("playlistPanelVideoRenderer")
                        val vId = r.get("videoId")?.asString
                        val title = r.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                        val artist = r.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Исполнитель"
                        val thumb = r.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""

                        if (!vId.isNullOrEmpty() && !title.isNullOrEmpty()) {
                            nextTracks.add(
                                Track(
                                    id = vId,
                                    title = title,
                                    artist = artist,
                                    thumbnailUrl = thumb
                                )
                            )
                        }
                    }
                    for (entry in obj.entrySet()) {
                        extractTracks(entry.value)
                    }
                } else if (element.isJsonArray) {
                    for (item in element.asJsonArray) {
                        extractTracks(item)
                    }
                }
            }

            extractTracks(root)
            Log.i(tag, "Получено ${nextTracks.size} треков в очереди Up Next для видео $videoId")
            WatchNextResult(nextTracks.distinctBy { it.id }, lyricsBrowseId, relatedBrowseId)
        } catch (e: Exception) {
            Log.e(tag, "Ошибка запроса WatchNext: ${e.message}")
            WatchNextResult(emptyList())
        }
    }
}
