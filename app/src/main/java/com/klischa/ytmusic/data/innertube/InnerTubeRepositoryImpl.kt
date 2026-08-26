package com.klischa.ytmusic.data.innertube

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.klischa.ytmusic.domain.model.StreamInfo
import com.klischa.ytmusic.domain.model.Track
import com.klischa.ytmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InnerTubeRepositoryImpl(
    private val api: InnerTubeApi = InnerTubeClient.api
) : MusicRepository {

    override suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val response = api.search(InnerTubeSearchRequest(query = query))
            if (response.isSuccessful && response.body() != null) {
                val jsonObject = response.body()!!
                val tracks = parseSearchJsonObject(jsonObject)
                Result.success(tracks)
            } else {
                Result.failure(Exception("Ошибка поиска InnerTube: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPlayer(InnerTubePlayerRequest(videoId = videoId))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val streamingData = body.getAsJsonObject("streamingData")

                if (streamingData != null) {
                    val formatsList = mutableListOf<FormatItem>()

                    fun extractFormats(array: JsonArray?) {
                        array?.forEach { element ->
                            if (element.isJsonObject) {
                                val obj = element.asJsonObject
                                val itag = obj.get("itag")?.asInt
                                val url = obj.get("url")?.asString
                                val mimeType = obj.get("mimeType")?.asString
                                val bitrate = obj.get("bitrate")?.asInt
                                val contentLength = obj.get("contentLength")?.asString
                                val signatureCipher = obj.get("signatureCipher")?.asString
                                val cipher = obj.get("cipher")?.asString

                                formatsList.add(
                                    FormatItem(
                                        itag = itag,
                                        url = url,
                                        mimeType = mimeType,
                                        bitrate = bitrate,
                                        averageBitrate = bitrate,
                                        contentLength = contentLength,
                                        signatureCipher = signatureCipher,
                                        cipher = cipher,
                                        audioQuality = null,
                                        approxDurationMs = null
                                    )
                                )
                            }
                        }
                    }

                    extractFormats(streamingData.getAsJsonArray("adaptiveFormats"))
                    extractFormats(streamingData.getAsJsonArray("formats"))

                    val audioFormats = formatsList
                        .filter { it.mimeType?.startsWith("audio/") == true }
                        .sortedByDescending { it.bitrate ?: 0 }

                    val bestFormat = audioFormats.firstOrNull() ?: formatsList.firstOrNull()

                    if (bestFormat != null) {
                        val resolvedUrl = CipherDecipherer.resolveAudioStreamUrl(bestFormat)
                        if (!resolvedUrl.isNullOrEmpty()) {
                            return@withContext Result.success(
                                StreamInfo(
                                    videoId = videoId,
                                    audioUrl = resolvedUrl,
                                    bitrate = bestFormat.bitrate ?: 128000,
                                    mimeType = bestFormat.mimeType ?: "audio/mp4",
                                    contentLength = bestFormat.contentLength?.toLongOrNull() ?: 0L
                                )
                            )
                        }
                    }
                }
            }

            Result.success(
                StreamInfo(
                    videoId = videoId,
                    audioUrl = "https://music.youtube.com/watch?v=$videoId",
                    bitrate = 128000,
                    mimeType = "audio/mp4",
                    contentLength = 0L
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingTracks(): Result<List<Track>> {
        return search("Хиты популярная музыка 2026")
    }

    private fun parseSearchJsonObject(root: JsonObject): List<Track> {
        val tracks = mutableListOf<Track>()

        fun recursiveExtract(element: JsonElement) {
            if (element.isJsonObject) {
                val obj = element.asJsonObject

                if (obj.has("musicResponsiveListItemRenderer")) {
                    val r = obj.getAsJsonObject("musicResponsiveListItemRenderer")
                    val flex = r.getAsJsonArray("flexColumns")

                    var title: String? = null
                    var artist = "Исполнитель"
                    var album = ""
                    var videoId: String? = null

                    if (flex != null && flex.size() > 0) {
                        val col0 = flex.get(0).asJsonObject
                        val textObj = col0.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")?.getAsJsonObject("text")
                        val runs = textObj?.getAsJsonArray("runs")
                        if (runs != null && runs.size() > 0) {
                            val firstRun = runs.get(0).asJsonObject
                            title = firstRun.get("text")?.asString
                            videoId = firstRun.getAsJsonObject("navigationEndpoint")
                                ?.getAsJsonObject("watchEndpoint")
                                ?.get("videoId")?.asString
                        }
                    }

                    if (videoId.isNullOrEmpty()) {
                        videoId = r.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
                    }

                    if (flex != null && flex.size() > 1) {
                        val col1 = flex.get(1).asJsonObject
                        val textObj = col1.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")?.getAsJsonObject("text")
                        val runs = textObj?.getAsJsonArray("runs")
                        if (runs != null && runs.size() > 0) {
                            artist = runs.get(0).asJsonObject.get("text")?.asString ?: "Исполнитель"
                            if (runs.size() > 2) {
                                album = runs.get(2).asJsonObject.get("text")?.asString ?: ""
                            }
                        }
                    }

                    var thumbUrl = ""
                    val thumbObj = r.getAsJsonObject("thumbnail")
                    if (thumbObj != null) {
                        val thumbs = thumbObj.getAsJsonObject("musicThumbnailRenderer")
                            ?.getAsJsonObject("thumbnail")
                            ?.getAsJsonArray("thumbnails")
                            ?: thumbObj.getAsJsonArray("thumbnails")

                        if (thumbs != null && thumbs.size() > 0) {
                            thumbUrl = thumbs.get(thumbs.size() - 1).asJsonObject.get("url")?.asString ?: ""
                        }
                    }

                    if (!title.isNullOrEmpty() && !videoId.isNullOrEmpty()) {
                        tracks.add(
                            Track(
                                id = videoId,
                                title = title,
                                artist = artist,
                                album = album,
                                thumbnailUrl = thumbUrl
                            )
                        )
                    }
                }

                for (entry in obj.entrySet()) {
                    recursiveExtract(entry.value)
                }
            } else if (element.isJsonArray) {
                for (item in element.asJsonArray) {
                    recursiveExtract(item)
                }
            }
        }

        recursiveExtract(root)
        return tracks.distinctBy { it.id }
    }
}
