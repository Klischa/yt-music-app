package com.klischa.ytmusic.data.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.klischa.ytmusic.domain.model.Track
import java.io.OutputStream

/**
 * Сохраняет аудиофайлы в системную медиатеку Android (MediaStore)
 * в выделенную папку Music/MyYTMusic с соблюдением Scoped Storage (Android 11-14).
 */
class MediaStoreSaver(private val context: Context) {

    private val tag = "MediaStoreSaver"

    fun createAudioEntry(track: Track, mimeType: String = "audio/mp4", extension: String = "m4a"): Pair<Uri, OutputStream>? {
        val resolver = context.contentResolver
        val cleanTitle = track.title.replace("[^a-zA-Z0-9а-яА-Я._ -]".toRegex(), "_")
        val fileName = "${cleanTitle}_${track.id}.$extension"

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.ALBUM, track.album.ifEmpty { "My YT Music" })
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MyYTMusic")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        return try {
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
            val outStream = resolver.openOutputStream(uri) ?: return null
            Pair(uri, outStream)
        } catch (e: Exception) {
            Log.e(tag, "Ошибка создания записи MediaStore: ${e.message}", e)
            null
        }
    }

    fun finishAudioEntry(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            try {
                context.contentResolver.update(uri, contentValues, null, null)
                Log.i(tag, "Аудиофайл успешно опубликован в Music/MyYTMusic: $uri")
            } catch (e: Exception) {
                Log.e(tag, "Ошибка обновления статуса MediaStore: ${e.message}")
            }
        }
    }
}
