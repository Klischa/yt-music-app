package com.klischa.ytmusic.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.klischa.ytmusic.R
import com.klischa.ytmusic.presentation.MainActivity

/**
 * Сервис фонового воспроизведения музыки на базе AndroidX Media3 (MediaLibraryService).
 * Обеспечивает потоковое воспроизведение Google/YouTube Media, работу при выключенном экране и системные уведомления.
 */
class PlaybackService : MediaLibraryService() {

    private val tag = "PlaybackService"

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "yt_music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Настройка сетевого источника данных с YouTube/Google заголовками
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(25000)
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        // 2. Инициализация ExoPlayer
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(tag, "Ошибка воспроизведения ExoPlayer: ${error.message} (${error.errorCodeName})", error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(tag, "Состояние воспроизведения: isPlaying = $isPlaying")
            }
        })

        // 3. PendingIntent для открытия MainActivity при клике на уведомление
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. Создание MediaLibrarySession с колбэками
        val sessionCallback = MediaSessionCallback(this, exoPlayer)

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, sessionCallback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        Log.i(tag, "PlaybackService успешно запущен и готов к воспроизведению")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомление управления воспроизведением музыки"
                setShowBadge(false)
                lockscreenVisibility = NotificationManager.IMPORTANCE_LOW
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        super.onDestroy()
        Log.i(tag, "PlaybackService уничтожен")
    }
}
