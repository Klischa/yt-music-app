package com.klischa.ytmusic.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.klischa.ytmusic.domain.model.LikeStatus
import com.klischa.ytmusic.domain.model.Playlist
import com.klischa.ytmusic.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Хранилище и менеджер плейлистов, понравившихся треков (лайков) и дизлайков.
 */
class PlaylistManager private constructor(context: Context) {

    private val tag = "PlaylistManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("yt_music_playlists_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks: StateFlow<List<Track>> = _likedTracks.asStateFlow()

    private val _dislikedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedTrackIds: StateFlow<Set<String>> = _dislikedTrackIds.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        try {
            // 1. Загрузка плейлистов
            val playlistsJson = prefs.getString(KEY_PLAYLISTS, null)
            if (!playlistsJson.isNullOrEmpty()) {
                val type = object : TypeToken<List<Playlist>>() {}.type
                val list: List<Playlist> = gson.fromJson(playlistsJson, type)
                _playlists.value = list
            }

            // 2. Загрузка лайков
            val likedJson = prefs.getString(KEY_LIKED_TRACKS, null)
            if (!likedJson.isNullOrEmpty()) {
                val type = object : TypeToken<List<Track>>() {}.type
                val list: List<Track> = gson.fromJson(likedJson, type)
                _likedTracks.value = list
            }

            // 3. Загрузка дизлайков
            val dislikedJson = prefs.getString(KEY_DISLIKED_IDS, null)
            if (!dislikedJson.isNullOrEmpty()) {
                val type = object : TypeToken<Set<String>>() {}.type
                val set: Set<String> = gson.fromJson(dislikedJson, type)
                _dislikedTrackIds.value = set
            }
        } catch (e: Exception) {
            Log.e(tag, "Ошибка чтения плейлистов: ${e.message}")
        }
    }

    private fun savePlaylists() {
        val json = gson.toJson(_playlists.value)
        prefs.edit().putString(KEY_PLAYLISTS, json).apply()
    }

    private fun saveLikes() {
        val json = gson.toJson(_likedTracks.value)
        prefs.edit().putString(KEY_LIKED_TRACKS, json).apply()
    }

    private fun saveDislikes() {
        val json = gson.toJson(_dislikedTrackIds.value)
        prefs.edit().putString(KEY_DISLIKED_IDS, json).apply()
    }

    // --- Плейлисты ---

    fun createPlaylist(title: String, description: String = ""): Playlist {
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Мой плейлист" },
            description = description,
            createdAt = System.currentTimeMillis()
        )
        val current = _playlists.value.toMutableList()
        current.add(0, newPlaylist)
        _playlists.value = current
        savePlaylists()
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        val current = _playlists.value.toMutableList()
        current.removeAll { it.id == playlistId }
        _playlists.value = current
        savePlaylists()
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            val tracks = playlist.tracks.toMutableList()
            if (tracks.none { it.id == track.id }) {
                tracks.add(track)
                val updated = playlist.copy(
                    tracks = tracks,
                    trackCount = tracks.size,
                    coverUrl = playlist.coverUrl.ifEmpty { track.thumbnailUrl }
                )
                current[index] = updated
                _playlists.value = current
                savePlaylists()
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            val tracks = playlist.tracks.toMutableList()
            tracks.removeAll { it.id == trackId }
            val updated = playlist.copy(
                tracks = tracks,
                trackCount = tracks.size,
                coverUrl = if (tracks.isEmpty()) "" else tracks.first().thumbnailUrl
            )
            current[index] = updated
            _playlists.value = current
            savePlaylists()
        }
    }

    // --- Лайки и Дизлайки ---

    fun toggleLike(track: Track): LikeStatus {
        val liked = _likedTracks.value.toMutableList()
        val disliked = _dislikedTrackIds.value.toMutableSet()

        val isAlreadyLiked = liked.any { it.id == track.id }
        disliked.remove(track.id)

        val resultStatus = if (isAlreadyLiked) {
            liked.removeAll { it.id == track.id }
            LikeStatus.NONE
        } else {
            liked.add(0, track)
            LikeStatus.LIKED
        }

        _likedTracks.value = liked
        _dislikedTrackIds.value = disliked

        saveLikes()
        saveDislikes()

        return resultStatus
    }

    fun toggleDislike(track: Track): LikeStatus {
        val liked = _likedTracks.value.toMutableList()
        val disliked = _dislikedTrackIds.value.toMutableSet()

        val isAlreadyDisliked = disliked.contains(track.id)
        liked.removeAll { it.id == track.id }

        val resultStatus = if (isAlreadyDisliked) {
            disliked.remove(track.id)
            LikeStatus.NONE
        } else {
            disliked.add(track.id)
            LikeStatus.DISLIKED
        }

        _likedTracks.value = liked
        _dislikedTrackIds.value = disliked

        saveLikes()
        saveDislikes()

        return resultStatus
    }

    fun getLikeStatus(trackId: String): LikeStatus {
        if (_likedTracks.value.any { it.id == trackId }) return LikeStatus.LIKED
        if (_dislikedTrackIds.value.contains(trackId)) return LikeStatus.DISLIKED
        return LikeStatus.NONE
    }

    companion object {
        private const val KEY_PLAYLISTS = "key_custom_playlists"
        private const val KEY_LIKED_TRACKS = "key_liked_tracks"
        private const val KEY_DISLIKED_IDS = "key_disliked_ids"

        @Volatile
        private var instance: PlaylistManager? = null

        fun getInstance(context: Context): PlaylistManager {
            return instance ?: synchronized(this) {
                instance ?: PlaylistManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
