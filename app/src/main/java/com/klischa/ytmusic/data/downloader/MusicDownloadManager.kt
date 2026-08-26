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

/**
 * Менеджер загрузки треков с проверкой свободного места и сохранением в MediaStore.
 */
class MusicDownloadManager(
    private val context: Context,
    private val repository: InnerTubeRepositoryImpl = InnerTubeRepositoryImpl()
) {
    private val tag = "MusicDownloadManager"
    private val okHttpClient = OkHttpClient()
    private val mediaStoreSaver = MediaStoreSaver(context)

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    /**
     * Проверяет, достаточно ли свободного места на устройстве (в байтах).
     */
    fun hasEnoughStorageSpace(requiredBytes: Long = 50 * 1024 * 1024L): Boolean {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes >= requiredBytes
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Запускает корутинную загрузку трека в MediaStore.
     */
    suspend fun downloadTrack(track: Track): Result<Uri> = withContext(Dispatchers.IO) {
        // 1. Проверка свободного места (минимум 20 МБ)
        if (!hasEnoughStorageSpace(20 * 1024 * 1024L)) {
            val error = "Недостаточно свободного места на диске для загрузки"
            updateState(track.id, DownloadState.Error(error))
            return@withContext Result.failure(IOException(error))
        }

        updateState(track.id, DownloadState.Downloading(0, 0, 0))

        try {
            // 2. Получение ссылки на поток через InnerTube API
            val streamInfoResult = repository.getStreamInfo(track.id)
            val streamInfo = streamInfoResult.getOrNull()
                ?: return@withContext Result.failure(Exception("Не удалось получить ссылку на поток"))

            // 3. Создание записи в MediaStore (папка Music/MyYTMusic)
            val isMp4 = streamInfo.mimeType.contains("mp4") || streamInfo.mimeType.contains("m4a")
            val ext = if (isMp4) "m4a" else "webm"
            val entry = mediaStoreSaver.createAudioEntry(track, streamInfo.mimeType, ext)
                ?: return@withContext Result.failure(IOException("Не удалось создать файл в MediaStore"))

            val (mediaStoreUri, outStream) = entry

            // 4. Загрузка потока через OkHttp чанками
            val request = Request.Builder()
                .url(streamInfo.audioUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                outStream.close()
                throw IOException("Ошибка скачивания: HTTP ${response.code}")
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
                            0
                        }
                        updateState(track.id, DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                    }
                    out.flush()
                }
            }

            // 5. Финализация записи в MediaStore
            mediaStoreSaver.finishAudioEntry(mediaStoreUri)
            updateState(track.id, DownloadState.Completed(mediaStoreUri, mediaStoreUri.toString()))

            Log.i(tag, "Трек '${track.title}' успешно загружен в Music/MyYTMusic")
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
