package com.klischa.ytmusic.data.lyrics

import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Сервис получения синхронизированных (LRC / Karaoke) и обычных текстов песен.
 * Поддерживает LrcLib API и официальный YouTube Music LyricFind.
 */
class LyricsService(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val tag = "LyricsService"

    data class LyricsLine(
        val timestampMs: Long,
        val text: String
    )

    data class LyricsResult(
        val isSynced: Boolean,
        val lines: List<LyricsLine>,
        val plainText: String,
        val source: String
    )

    suspend fun getLyrics(title: String, artist: String): LyricsResult? = withContext(Dispatchers.IO) {
        // 1. Пробуем LrcLib для получения синхронизированного построчного текста (Karaoke)
        try {
            val cleanTitle = cleanSearchTerm(title)
            val cleanArtist = cleanSearchTerm(artist)

            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")

            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyYTMusicApp/1.0 (Android)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JsonParser.parseString(body).asJsonObject

                val syncedLyrics = json.get("syncedLyrics")?.asString
                val plainLyrics = json.get("plainLyrics")?.asString

                if (!syncedLyrics.isNullOrEmpty()) {
                    val parsedLines = parseLrcString(syncedLyrics)
                    if (parsedLines.isNotEmpty()) {
                        Log.i(tag, "Получен синхронизированный текст песни (LrcLib): ${parsedLines.size} строк")
                        return@withContext LyricsResult(
                            isSynced = true,
                            lines = parsedLines,
                            plainText = plainLyrics ?: syncedLyrics,
                            source = "LrcLib Synced"
                        )
                    }
                }

                if (!plainLyrics.isNullOrEmpty()) {
                    val lines = plainLyrics.lines().map { LyricsLine(0L, it) }
                    return@withContext LyricsResult(
                        isSynced = false,
                        lines = lines,
                        plainText = plainLyrics,
                        source = "LrcLib Plain"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Ошибка LrcLib: ${e.message}")
        }

        null
    }

    private fun parseLrcString(lrcContent: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?\\](.*)")

        for (rawLine in lrcContent.lines()) {
            val matcher = pattern.matcher(rawLine.trim())
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3) ?: "00"
                val ms = if (msStr.length == 2) (msStr.toLongOrNull() ?: 0L) * 10 else (msStr.toLongOrNull() ?: 0L)
                val text = matcher.group(4)?.trim() ?: ""

                val totalMs = (min * 60 + sec) * 1000 + ms
                if (text.isNotEmpty()) {
                    lines.add(LyricsLine(totalMs, text))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("\\(.*?\\)"), "") // Удаляем текст в скобках (Official Video, Feat, etc.)
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("(?i)ft\\.?|feat\\.?"), "")
            .replace("Композиция", "")
            .replace("Видео", "")
            .trim()
    }
}
