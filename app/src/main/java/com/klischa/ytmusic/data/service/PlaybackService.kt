package com.klischa.ytmusic.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.klischa.ytmusic.R
import com.klischa.ytmusic.domain.model.Track
import com.klischa.ytmusic.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Непрерывный фоновый сервис воспроизведения музыки (Foreground Media Playback Service).
 * Гарантирует воспроизведение при заблокированном экране на Android 14 (API 34)
 * с поддержкой системных медиа-уведомлений, экрана блокировки и гарнитур.
 */
class PlaybackService : MediaLibraryService() {

    private val tag = "PlaybackService"

    private var exoPlayer: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    var audioWebView: YouTubeAudioWebView? = null
        private set

    private var currentTrack: Track? = null
    private var isPlayingState = false
    private var isLocalPlayback = false

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "yt_music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.klischa.ytmusic.ACTION_PLAY"
        const val ACTION_PAUSE = "com.klischa.ytmusic.ACTION_PAUSE"
        const val ACTION_PREV = "com.klischa.ytmusic.ACTION_PREV"
        const val ACTION_NEXT = "com.klischa.ytmusic.ACTION_NEXT"

        @Volatile
        var instance: PlaybackService? = null
            private set
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausePlayback()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        // 1. Захват WakeLock и WifiLock для непрерывного фонового стриминга
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyYTMusic:AudioWakeLock").apply {
                setReferenceCounted(false)
            }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiLock = wifiManager?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyYTMusic:WifiLock")
        } catch (e: Exception) {
            Log.w(tag, "Ошибка инициализации блокировок питания: ${e.message}")
        }

        // 2. Инициализация ExoPlayer
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer = player

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isLocalPlayback) {
                    isPlayingState = isPlaying
                    updateForegroundNotification()
                }
            }
        })

        // 3. Инициализация фонового HTML5 Audio Engine
        audioWebView = YouTubeAudioWebView(this)

        // 4. Регистрация ресивера отключения наушников
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)

        // 5. Создание MediaLibrarySession
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCallback = MediaSessionCallback(this, player)
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        Log.i(tag, "PlaybackService успешно запущен в фоновом режиме")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_PREV -> onPreviousRequested()
            ACTION_NEXT -> onNextRequested()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    fun startPlayingTrack(track: Track) {
        currentTrack = track
        isPlayingState = true

        wakeLock?.acquire(6 * 3600 * 1000L)
        wifiLock?.acquire()

        // 1. Запуск в режиме Foreground Service для Android 14
        startForegroundServiceWithNotification(track, true)

        // 2. Выбор движка воспроизведения
        if (track.localUri != null) {
            isLocalPlayback = true
            audioWebView?.pause()
            val mediaItem = track.toMediaItem()
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
        } else {
            isLocalPlayback = false
            exoPlayer?.pause()
            audioWebView?.playTrack(track.id)
            audioWebView?.keepAliveInBackground()
        }

        Log.i(tag, "Запущено непрерывное фоновое воспроизведение трека: ${track.title}")
    }

    fun pausePlayback() {
        isPlayingState = false
        if (isLocalPlayback) {
            exoPlayer?.pause()
        } else {
            audioWebView?.pause()
        }
        updateForegroundNotification()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    fun resumePlayback() {
        isPlayingState = true
        wakeLock?.acquire(6 * 3600 * 1000L)
        if (isLocalPlayback) {
            exoPlayer?.play()
        } else {
            audioWebView?.resume()
        }
        updateForegroundNotification()
    }

    fun seekTo(positionMs: Long) {
        if (isLocalPlayback) {
            exoPlayer?.seekTo(positionMs)
        } else {
            audioWebView?.seekTo(positionMs)
        }
    }

    private var onSkipNextListener: (() -> Unit)? = null
    private var onSkipPrevListener: (() -> Unit)? = null

    fun setQueueNavigationListeners(onPrev: () -> Unit, onNext: () -> Unit) {
        onSkipPrevListener = onPrev
        onSkipNextListener = onNext
    }

    private fun onPreviousRequested() {
        onSkipPrevListener?.invoke()
    }

    private fun onNextRequested() {
        onSkipNextListener?.invoke()
    }

    private fun startForegroundServiceWithNotification(track: Track, isPlaying: Boolean) {
        val notification = buildMediaNotification(track, isPlaying)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "Ошибка startForeground: ${e.message}", e)
        }
    }

    private fun updateForegroundNotification() {
        val track = currentTrack ?: return
        val notification = buildMediaNotification(track, isPlayingState)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildMediaNotification(track: Track, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this, 1, Intent(this, PlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val playPauseIntent = PendingIntent.getService(
            this, 2, Intent(this, PlaybackService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this, 3, Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(track.title)
            .setContentText(track.artist + if (track.album.isNotEmpty()) " • ${track.album}" else "")
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_skip_previous, "Предыдущий", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Пауза" else "Играть", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Следующий", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновое воспроизведение музыки"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(noisyReceiver)
        } catch (ignored: Exception) {}

        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        wakeLock = null
        wifiLock = null

        audioWebView?.release()
        audioWebView = null

        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        exoPlayer = null
        instance = null
        super.onDestroy()
        Log.i(tag, "PlaybackService остановлен")
    }
}
