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

    private var progressJob: Job? = null

    init {
        initMediaController()
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

    private fun setupPlayerListener(controller: MediaController?) {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                val track = _queue.value.find { it.id == mediaId }
                if (track != null) {
                    _currentTrack.value = track
                }
            }
        })
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        _currentTrack.value = track
        _queue.value = newQueue

        viewModelScope.launch {
            // Если трек уже скачан локально — играем напрямую из файла
            if (track.localUri != null) {
                startPlaybackWithUri(track, track.localUri.toString())
                return@launch
            }

            // Получаем аудиопоток через мульти-стратегический резолвер
            val streamResult = repository.getStreamInfo(track.id)
            val streamInfo = streamResult.getOrNull()

            if (streamInfo != null && streamInfo.audioUrl.isNotEmpty() && streamInfo.audioUrl.startsWith("http")) {
                val playableTrack = track.copy(streamUrl = streamInfo.audioUrl)
                _currentTrack.value = playableTrack
                startPlaybackWithUri(playableTrack, streamInfo.audioUrl)
            } else {
                val err = streamResult.exceptionOrNull()?.message ?: "Не удалось получить аудиопоток трека"
                Toast.makeText(getApplication(), err, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPlaybackWithUri(track: Track, uriString: String) {
        val playAction = { controller: MediaController ->
            val mediaItem = track.toMediaItem().buildUpon()
                .setUri(uriString)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }

        val controller = mediaController
        if (controller != null) {
            playAction(controller)
        } else {
            controllerFuture?.addListener({
                try {
                    val ctrl = controllerFuture?.get()
                    ctrl?.let { playAction(it) }
                } catch (ignored: Exception) {}
            }, MoreExecutors.directExecutor())
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
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
                Toast.makeText(getApplication(), "Трек '${track.title}' сохранён в Music/MyYTMusic!", Toast.LENGTH_LONG).show()
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
        stopProgressTracking()
    }
}
