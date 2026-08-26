package com.klischa.ytmusic.data.innertube

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
            if (response.isSuccessful) {
                val tracks = parseSearchResponse(response.body())
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
            if (response.isSuccessful) {
                val body = response.body()
                val streamingData = body?.streamingData ?: return@withContext Result.failure(Exception("Нет данных потока (StreamingData)"))

                // Ищем лучший аудиоформат (mp4a / opus с максимальным битрейтом)
                val audioFormats = (streamingData.adaptiveFormats ?: emptyList())
                    .filter { it.mimeType?.startsWith("audio/") == true }
                    .sortedByDescending { it.bitrate ?: 0 }

                val bestFormat = audioFormats.firstOrNull() ?: streamingData.formats?.firstOrNull()
                    ?: return@withContext Result.failure(Exception("Аудиоформат не найден для трека"))

                val resolvedUrl = CipherDecipherer.resolveAudioStreamUrl(bestFormat)
                    ?: return@withContext Result.failure(Exception("Не удалось расшифровать ссылку аудиопотока"))

                val streamInfo = StreamInfo(
                    videoId = videoId,
                    audioUrl = resolvedUrl,
                    bitrate = bestFormat.bitrate ?: 128000,
                    mimeType = bestFormat.mimeType ?: "audio/mp4",
                    contentLength = bestFormat.contentLength?.toLongOrNull() ?: 0L,
                    expiresInSeconds = streamingData.expiresInSeconds?.toLongOrNull() ?: 21600L
                )
                Result.success(streamInfo)
            } else {
                Result.failure(Exception("Ошибка получения потока: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingTracks(): Result<List<Track>> {
        return search("Популярная музыка новинки треки")
    }

    private fun parseSearchResponse(body: InnerTubeSearchResponse?): List<Track> {
        val tracks = mutableListOf<Track>()
        if (body == null) return tracks

        val sections = body.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.contents
            ?: body.contents?.sectionListRenderer?.contents
            ?: emptyList()

        for (section in sections) {
            val items = section.musicShelfRenderer?.contents
                ?: section.itemSectionRenderer?.contents
                ?: emptyList()

            for (item in items) {
                // Вариант 1: MusicResponsiveListItemRenderer
                item.musicResponsiveListItemRenderer?.let { renderer ->
                    val videoId = renderer.playlistItemData?.videoId
                        ?: renderer.flexColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId

                    if (videoId != null) {
                        val title = renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs?.firstOrNull()?.text ?: "Трек"

                        val artistRuns = renderer.flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs
                        val artist = artistRuns?.firstOrNull()?.text ?: "Исполнитель"
                        val album = artistRuns?.getOrNull(2)?.text ?: ""

                        val thumbUrl = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url
                            ?: renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""

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

                // Вариант 2: CompactVideoRenderer
                item.compactVideoRenderer?.let { renderer ->
                    val videoId = renderer.videoId
                    if (videoId != null) {
                        val title = renderer.title?.runs?.firstOrNull()?.text ?: "Видео"
                        val artist = renderer.longBylineText?.runs?.firstOrNull()?.text ?: "Автор"
                        val thumbUrl = renderer.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""

                        tracks.add(
                            Track(
                                id = videoId,
                                title = title,
                                artist = artist,
                                thumbnailUrl = thumbUrl
                            )
                        )
                    }
                }
            }
        }

        return tracks.distinctBy { it.id }
    }
}
