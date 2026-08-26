package com.klischa.ytmusic.data.innertube

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.klischa.ytmusic.domain.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Мульти-стратегический резолвер аудиопотоков (Multi-Source YouTube Audio Stream Resolver).
 * Использует каскад из InnerTube API, Embedded Player и открытых шлюзов для гарантированного
 * получения рабочей прямой ссылки на звук.
 */
class AudioStreamResolver(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val tag = "AudioStreamResolver"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun resolveAudioStream(videoId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        // Стратегия 1: YouTube Music Web Remix InnerTube Player
        try {
            val stream1 = tryInnerTubeWebRemix(videoId)
            if (stream1 != null && stream1.audioUrl.startsWith("http")) {
                Log.i(tag, "Аудиопоток успешно получен через Strategy 1 (WEB_REMIX)")
                return@withContext Result.success(stream1)
            }
        } catch (e: Exception) {
            Log.w(tag, "Strategy 1 ошибка: ${e.message}")
        }

        // Стратегия 2: Embedded Player / TV Endpoint
        try {
            val stream2 = tryEmbeddedPlayer(videoId)
            if (stream2 != null && stream2.audioUrl.startsWith("http")) {
                Log.i(tag, "Аудиопоток успешно получен через Strategy 2 (Embedded)")
                return@withContext Result.success(stream2)
            }
        } catch (e: Exception) {
            Log.w(tag, "Strategy 2 ошибка: ${e.message}")
        }

        // Стратегия 3: Публичные Invidious / Piped API шлюзы
        try {
            val stream3 = tryPublicGateways(videoId)
            if (stream3 != null && stream3.audioUrl.startsWith("http")) {
                Log.i(tag, "Аудиопоток успешно получен через Strategy 3 (Public Gateway)")
                return@withContext Result.success(stream3)
            }
        } catch (e: Exception) {
            Log.w(tag, "Strategy 3 ошибка: ${e.message}")
        }

        Result.failure(IOException("Не удалось получить ссылку на аудиопоток для трека $videoId"))
    }

    private fun tryInnerTubeWebRemix(videoId: String): StreamInfo? {
        val url = "https://music.youtube.com/youtubei/v1/player"
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
                "videoId": "$videoId",
                "playbackContext": {
                    "contentPlaybackContext": {
                        "html5Preference": "HTML5_PREF_WANTS"
                    }
                }
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

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val responseBody = response.body?.string() ?: return null
        return parsePlayerResponseJson(videoId, responseBody)
    }

    private fun tryEmbeddedPlayer(videoId: String): StreamInfo? {
        val url = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        val payload = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_EMBEDDED_PLAYER",
                        "clientVersion": "1.20240401.01.00",
                        "hl": "ru",
                        "gl": "RU"
                    },
                    "thirdParty": {
                        "embedUrl": "https://www.youtube.com/embed/$videoId"
                    }
                },
                "videoId": "$videoId",
                "playbackContext": {
                    "contentPlaybackContext": {
                        "html5Preference": "HTML5_PREF_WANTS"
                    }
                }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .header("Referer", "https://www.youtube.com/embed/$videoId")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val responseBody = response.body?.string() ?: return null
        return parsePlayerResponseJson(videoId, responseBody)
    }

    private fun tryPublicGateways(videoId: String): StreamInfo? {
        val gateways = listOf(
            "https://pipedapi.kavin.rocks/streams/$videoId",
            "https://api.piped.privacydev.net/streams/$videoId",
            "https://yewtu.be/api/v1/videos/$videoId",
            "https://inv.nadeko.net/api/v1/videos/$videoId"
        )

        for (gatewayUrl in gateways) {
            try {
                val request = Request.Builder()
                    .url(gatewayUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val json = JsonParser.parseString(body).asJsonObject

                    val audioStreams = json.getAsJsonArray("audioStreams") ?: json.getAsJsonArray("adaptiveFormats")
                    if (audioStreams != null && audioStreams.size() > 0) {
                        val firstAudio = audioStreams.get(0).asJsonObject
                        val audioUrl = firstAudio.get("url")?.asString
                        val mime = firstAudio.get("mimeType")?.asString ?: "audio/mp4"
                        val bitrate = firstAudio.get("bitrate")?.asInt ?: 128000

                        if (!audioUrl.isNullOrEmpty()) {
                            return StreamInfo(
                                videoId = videoId,
                                audioUrl = audioUrl,
                                bitrate = bitrate,
                                mimeType = mime,
                                contentLength = 0L
                            )
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }
        return null
    }

    private fun parsePlayerResponseJson(videoId: String, jsonString: String): StreamInfo? {
        val root = JsonParser.parseString(jsonString).asJsonObject
        val streamingData = root.getAsJsonObject("streamingData") ?: return null

        val formatsList = mutableListOf<FormatItem>()

        fun extract(array: JsonArray?) {
            array?.forEach { element ->
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    val itag = obj.get("itag")?.asInt
                    val url = obj.get("url")?.asString
                    val mime = obj.get("mimeType")?.asString
                    val bitrate = obj.get("bitrate")?.asInt
                    val cipher = obj.get("signatureCipher")?.asString ?: obj.get("cipher")?.asString

                    formatsList.add(
                        FormatItem(
                            itag = itag,
                            url = url,
                            mimeType = mime,
                            bitrate = bitrate,
                            averageBitrate = bitrate,
                            contentLength = obj.get("contentLength")?.asString,
                            signatureCipher = cipher,
                            cipher = cipher,
                            audioQuality = null,
                            approxDurationMs = null
                        )
                    )
                }
            }
        }

        extract(streamingData.getAsJsonArray("adaptiveFormats"))
        extract(streamingData.getAsJsonArray("formats"))

        // Приоритет: чистые аудиоформаты (m4a, opus)
        val audioFormats = formatsList
            .filter { it.mimeType?.startsWith("audio/") == true }
            .sortedByDescending { it.bitrate ?: 0 }

        val bestFormat = audioFormats.firstOrNull() ?: formatsList.firstOrNull() ?: return null
        val resolvedUrl = CipherDecipherer.resolveAudioStreamUrl(bestFormat) ?: return null

        return StreamInfo(
            videoId = videoId,
            audioUrl = resolvedUrl,
            bitrate = bestFormat.bitrate ?: 128000,
            mimeType = bestFormat.mimeType ?: "audio/mp4",
            contentLength = bestFormat.contentLength?.toLongOrNull() ?: 0L
        )
    }
}
