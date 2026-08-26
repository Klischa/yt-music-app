package com.klischa.ytmusic.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.klischa.ytmusic.R
import com.klischa.ytmusic.presentation.MainActivity

/**
 * Сервис фонового воспроизведения музыки на базе AndroidX Media3 (MediaLibraryService).
 * Обеспечивает воспроизведение при заблокированном экране, управление очередью и системное уведомление.
 */
class PlaybackService : MediaLibraryService() {

    private val tag = "PlaybackService"

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "yt_music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Захват частичного WakeLock для предотвращения засыпания CPU при выключенном экране
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyYTMusic:PlaybackWakeLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.w(tag, "Не удалось создать WakeLock: ${e.message}")
        }

        // 2. Инициализация ExoPlayer с аудиоатрибутами для музыки
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    wakeLock?.acquire(4 * 3600 * 1000L) // 4 часа макс
                } else {
                    if (wakeLock?.isHeld == true) wakeLock?.release()
                }
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

        Log.i(tag, "PlaybackService успешно запущен и инициализирован с WakeLock")
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
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null

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
