package com.klischa.ytmusic.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.klischa.ytmusic.data.downloader.MusicDownloadManager
import com.klischa.ytmusic.data.innertube.InnerTubeRepositoryImpl
import com.klischa.ytmusic.data.innertube.WatchNextRepository
import com.klischa.ytmusic.data.local.PlaylistManager
import com.klischa.ytmusic.data.lyrics.LyricsService
import com.klischa.ytmusic.data.service.PlaybackService
import com.klischa.ytmusic.domain.model.DownloadState
import com.klischa.ytmusic.domain.model.LikeStatus
import com.klischa.ytmusic.domain.model.Playlist
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
    private val lyricsService = LyricsService()
    private val watchNextRepo = WatchNextRepository(application)
    val playlistManager = PlaylistManager.getInstance(application)

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

    private val _lyrics = MutableStateFlow<LyricsService.LyricsResult?>(null)
    val lyrics: StateFlow<LyricsService.LyricsResult?> = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _currentTrackLikeStatus = MutableStateFlow(LikeStatus.NONE)
    val currentTrackLikeStatus: StateFlow<LikeStatus> = _currentTrackLikeStatus.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistManager.playlists
    val likedTracks: StateFlow<List<Track>> = playlistManager.likedTracks
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.downloadStates

    private var progressJob: Job? = null

    init {
        startAndBindPlaybackService()
        observeServiceAudioEngine()
    }

    private fun startAndBindPlaybackService() {
        val serviceIntent = Intent(getApplication(), PlaybackService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(serviceIntent)
            } else {
                getApplication<Application>().startService(serviceIntent)
            }
        } catch (ignored: Exception) {}

        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
            } catch (ignored: Exception) {}
        }, MoreExecutors.directExecutor())
    }

    fun createAndAttachAudioPlayer(context: Context): View {
        val service = PlaybackService.instance
        if (service != null && service.audioWebView != null) {
            return service.audioWebView!!.createAndAttachWebView(context)
        }
        val fallback = com.klischa.ytmusic.data.service.YouTubeAudioWebView(context)
        return fallback.createAndAttachWebView(context)
    }

    private fun observeServiceAudioEngine() {
        viewModelScope.launch {
            while (isActive) {
                val service = PlaybackService.instance
                if (service != null && service.audioWebView != null) {
                    service.setQueueNavigationListeners(
                        onPrev = { skipPrevious() },
                        onNext = { skipNext() }
                    )
                    launch {
                        service.audioWebView!!.isPlaying.collect { _isPlaying.value = it }
                    }
                    launch {
                        service.audioWebView!!.currentPositionMs.collect { _currentPositionMs.value = it }
                    }
                    launch {
                        service.audioWebView!!.durationMs.collect { dur ->
                            if (dur > 0) _durationMs.value = dur
                        }
                    }
                    break
                }
                delay(300)
            }
        }
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        _currentTrack.value = track
        _queue.value = newQueue
        _lyrics.value = null
        _currentTrackLikeStatus.value = playlistManager.getLikeStatus(track.id)
        _durationMs.value = (track.durationSeconds * 1000L).coerceAtLeast(1000L)
        _isPlaying.value = true

        loadLyrics(track)
        loadWatchNext(track)

        val service = PlaybackService.instance
        if (service != null) {
            service.startPlayingTrack(track)
        } else {
            startAndBindPlaybackService()
            viewModelScope.launch {
                delay(500)
                PlaybackService.instance?.startPlayingTrack(track)
            }
        }
    }

    fun toggleLike(track: Track) {
        val newStatus = playlistManager.toggleLike(track)
        if (_currentTrack.value?.id == track.id) {
            _currentTrackLikeStatus.value = newStatus
        }
        val msg = if (newStatus == LikeStatus.LIKED) "Добавлено в Понравившиеся ❤️" else "Удалено из Понравившихся"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    fun toggleDislike(track: Track) {
        val newStatus = playlistManager.toggleDislike(track)
        if (_currentTrack.value?.id == track.id) {
            _currentTrackLikeStatus.value = newStatus
        }
        if (newStatus == LikeStatus.DISLIKED) {
            Toast.makeText(getApplication(), "Дизлайк: трек будет пропускаться 👎", Toast.LENGTH_SHORT).show()
            skipNext()
        }
    }

    fun createPlaylist(title: String, description: String = "") {
        playlistManager.createPlaylist(title, description)
        Toast.makeText(getApplication(), "Плейлист '$title' создан!", Toast.LENGTH_SHORT).show()
    }

    fun deletePlaylist(playlistId: String) {
        playlistManager.deletePlaylist(playlistId)
        Toast.makeText(getApplication(), "Плейлист удалён", Toast.LENGTH_SHORT).show()
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        playlistManager.addTrackToPlaylist(playlistId, track)
        Toast.makeText(getApplication(), "Трек добавлен в плейлист!", Toast.LENGTH_SHORT).show()
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistManager.removeTrackFromPlaylist(playlistId, trackId)
    }

    private fun loadLyrics(track: Track) {
        viewModelScope.launch {
            _isLyricsLoading.value = true
            val res = lyricsService.getLyrics(track.title, track.artist)
            _lyrics.value = res
            _isLyricsLoading.value = false
        }
    }

    private fun loadWatchNext(track: Track) {
        viewModelScope.launch {
            val result = watchNextRepo.getWatchNext(track.id)
            if (result.upNextTracks.isNotEmpty()) {
                val currentList = _queue.value.toMutableList()
                for (nextTrack in result.upNextTracks) {
                    if (currentList.none { it.id == nextTrack.id }) {
                        currentList.add(nextTrack)
                    }
                }
                _queue.value = currentList
            }
        }
    }

    fun togglePlayPause() {
        val service = PlaybackService.instance
        if (_isPlaying.value) {
            service?.pausePlayback()
            _isPlaying.value = false
        } else {
            service?.resumePlayback()
            _isPlaying.value = true
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        PlaybackService.instance?.seekTo(positionMs)
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

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        progressJob?.cancel()
    }
}
