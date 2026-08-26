package com.klischa.ytmusic.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.klischa.ytmusic.data.downloader.MusicDownloadManager
import com.klischa.ytmusic.data.innertube.InnerTubeRepositoryImpl
import com.klischa.ytmusic.data.service.PlaybackService
import com.klischa.ytmusic.data.service.YouTubeAudioPlayerBridge
import com.klischa.ytmusic.domain.model.DownloadState
import com.klischa.ytmusic.domain.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InnerTubeRepositoryImpl(application)
    private val downloadManager = MusicDownloadManager(application, repository)
    private val youTubeBridge = YouTubeAudioPlayerBridge(application)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.downloadStates

    private var isPlayingLocalFile = false
    private var progressJob: Job? = null

    init {
        initMediaController()
        observeYouTubeBridge()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                mediaController = controller
                setupPlayerListener(controller)
            } catch (ignored: Exception) {}
        }, MoreExecutors.directExecutor())
    }

    private fun observeYouTubeBridge() {
        viewModelScope.launch {
            youTubeBridge.isPlaying.collect { playing ->
                if (!isPlayingLocalFile) {
                    _isPlaying.value = playing
                }
            }
        }
        viewModelScope.launch {
            youTubeBridge.currentPositionMs.collect { pos ->
                if (!isPlayingLocalFile) {
                    _currentPositionMs.value = pos
                }
            }
        }
        viewModelScope.launch {
            youTubeBridge.durationMs.collect { dur ->
                if (!isPlayingLocalFile && dur > 0) {
                    _durationMs.value = dur
                }
            }
        }
    }

    private fun setupPlayerListener(controller: MediaController?) {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlayingLocalFile) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressTracking()
                    } else {
                        stopProgressTracking()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isPlayingLocalFile && playbackState == Player.STATE_READY) {
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        _currentTrack.value = track
        _queue.value = newQueue

        // 1. Если трек уже скачан локально — играем через ExoPlayer
        if (track.localUri != null) {
            isPlayingLocalFile = true
            youTubeBridge.pause()
            startLocalPlayback(track)
            return
        }

        // 2. Для онлайн-треков запускаем воспроизведение через YouTube Player Engine
        isPlayingLocalFile = false
        mediaController?.pause()
        youTubeBridge.playVideo(track.id)
        _isPlaying.value = true
        _durationMs.value = (track.durationSeconds * 1000L).coerceAtLeast(180_000L)
    }

    private fun startLocalPlayback(track: Track) {
        val controller = mediaController ?: return
        val mediaItem = track.toMediaItem()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        if (isPlayingLocalFile) {
            val controller = mediaController ?: return
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        } else {
            youTubeBridge.togglePlayPause()
        }
    }

    fun seekTo(positionMs: Long) {
        if (isPlayingLocalFile) {
            mediaController?.seekTo(positionMs)
        } else {
            youTubeBridge.seekTo(positionMs)
        }
        _currentPositionMs.value = positionMs
    }

    fun skipNext() {
        val current = _currentTrack.value ?: return
        val currentQueue = _queue.value
        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex + 1 < currentQueue.size) {
            playTrack(currentQueue[currentIndex + 1], currentQueue)
        }
    }

    fun skipPrevious() {
        val current = _currentTrack.value ?: return
        val currentQueue = _queue.value
        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            playTrack(currentQueue[currentIndex - 1], currentQueue)
        }
    }

    fun setFullPlayerExpanded(expanded: Boolean) {
        _isFullPlayerExpanded.value = expanded
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            val result = downloadManager.downloadTrack(track)
            result.onSuccess {
                Toast.makeText(getApplication(), "Трек '${track.title}' успешно сохранён в Music/MyYTMusic!", Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                Toast.makeText(getApplication(), "Ошибка скачивания: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPositionMs.value = controller.currentPosition
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        youTubeBridge.release()
        stopProgressTracking()
    }
}
