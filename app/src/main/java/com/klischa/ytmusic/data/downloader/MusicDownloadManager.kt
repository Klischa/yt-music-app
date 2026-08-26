package com.klischa.ytmusic.data.downloader

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.klischa.ytmusic.data.innertube.InnerTubeRepositoryImpl
import com.klischa.ytmusic.domain.model.DownloadState
import com.klischa.ytmusic.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Менеджер загрузки треков с проверкой свободного места, валидацией аудиопотока и сохранением в MediaStore.
 */
class MusicDownloadManager(
    private val context: Context,
    private val repository: InnerTubeRepositoryImpl = InnerTubeRepositoryImpl(context)
) {
    private val tag = "MusicDownloadManager"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val mediaStoreSaver = MediaStoreSaver(context)

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    fun hasEnoughStorageSpace(requiredBytes: Long = 20 * 1024 * 1024L): Boolean {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes >= requiredBytes
        } catch (e: Exception) {
            true
        }
    }

    suspend fun downloadTrack(track: Track): Result<Uri> = withContext(Dispatchers.IO) {
        if (!hasEnoughStorageSpace(20 * 1024 * 1024L)) {
            val error = "Недостаточно свободного места на диске для загрузки"
            updateState(track.id, DownloadState.Error(error))
            return@withContext Result.failure(IOException(error))
        }

        updateState(track.id, DownloadState.Downloading(0, 0, 0))

        try {
            // 1. Получение прямой ссылки на аудиопоток
            val streamInfoResult = repository.getStreamInfo(track.id)
            val streamInfo = streamInfoResult.getOrNull()
                ?: return@withContext Result.failure(Exception("Не удалось получить аудиопоток трека"))

            if (streamInfo.audioUrl.isEmpty() || !streamInfo.audioUrl.startsWith("http")) {
                val err = "Некорректная ссылка на аудиопоток"
                updateState(track.id, DownloadState.Error(err))
                return@withContext Result.failure(IOException(err))
            }

            // 2. Создание записи в MediaStore
            val isMp4 = streamInfo.mimeType.contains("mp4") || streamInfo.mimeType.contains("m4a")
            val ext = if (isMp4) "m4a" else "webm"
            val entry = mediaStoreSaver.createAudioEntry(track, streamInfo.mimeType, ext)
                ?: return@withContext Result.failure(IOException("Не удалось создать запись в MediaStore"))

            val (mediaStoreUri, outStream) = entry

            // 3. Скачивание аудиопотока
            val request = Request.Builder()
                .url(streamInfo.audioUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val contentType = response.header("Content-Type") ?: ""

            if (!response.isSuccessful || contentType.contains("text/html")) {
                outStream.close()
                context.contentResolver.delete(mediaStoreUri, null, null)
                val err = "Сервер вернул некорректный ответ (HTML вместо аудио)"
                updateState(track.id, DownloadState.Error(err))
                return@withContext Result.failure(IOException(err))
            }

            val body = response.body ?: throw IOException("Пустой ответ от сервера")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { inStream ->
                outStream.use { out ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloadedBytes += read

                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            50
                        }
                        updateState(track.id, DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                    }
                    out.flush()
                }
            }

            // Проверка минимального размера (полноценный трек весит минимум 500 КБ)
            if (downloadedBytes < 200 * 1024L) {
                context.contentResolver.delete(mediaStoreUri, null, null)
                val err = "Загруженный файл повреждён или имеет слишком малый размер (${downloadedBytes / 1024} KB)"
                updateState(track.id, DownloadState.Error(err))
                return@withContext Result.failure(IOException(err))
            }

            mediaStoreSaver.finishAudioEntry(mediaStoreUri)
            updateState(track.id, DownloadState.Completed(mediaStoreUri, mediaStoreUri.toString()))

            Log.i(tag, "Трек '${track.title}' (${downloadedBytes / 1024} KB) успешно загружен в Music/MyYTMusic")
            Result.success(mediaStoreUri)

        } catch (e: Exception) {
            Log.e(tag, "Ошибка загрузки трека: ${e.message}", e)
            updateState(track.id, DownloadState.Error(e.message ?: "Ошибка загрузки"))
            Result.failure(e)
        }
    }

    private fun updateState(trackId: String, state: DownloadState) {
        val current = _downloadStates.value.toMutableMap()
        current[trackId] = state
        _downloadStates.value = current
    }
}
