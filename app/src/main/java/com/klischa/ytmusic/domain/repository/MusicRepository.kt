package com.klischa.ytmusic.domain.repository

import com.klischa.ytmusic.domain.model.StreamInfo
import com.klischa.ytmusic.domain.model.Track

interface MusicRepository {
    suspend fun search(query: String): Result<List<Track>>
    suspend fun getStreamInfo(videoId: String): Result<StreamInfo>
    suspend fun getTrendingTracks(): Result<List<Track>>
}
