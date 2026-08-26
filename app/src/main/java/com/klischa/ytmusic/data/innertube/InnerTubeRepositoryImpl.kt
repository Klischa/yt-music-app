package com.klischa.ytmusic.data.innertube

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.klischa.ytmusic.domain.model.StreamInfo
import com.klischa.ytmusic.domain.model.Track
import com.klischa.ytmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InnerTubeRepositoryImpl(
    private val api: InnerTubeApi = InnerTubeClient.api,
    private val streamResolver: AudioStreamResolver = AudioStreamResolver()
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
        streamResolver.resolveAudioStream(videoId)
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
