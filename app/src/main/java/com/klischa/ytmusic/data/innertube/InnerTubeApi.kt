package com.klischa.ytmusic.data.innertube

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface InnerTubeApi {

    @Headers(
        "Content-Type: application/json",
        "User-Agent: com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; ru_RU; Infinix X6833B)",
        "X-YouTube-Client-Name: 21", // ANDROID_MUSIC
        "X-YouTube-Client-Version: 6.42.52"
    )
    @POST("youtubei/v1/search")
    suspend fun search(
        @Body request: InnerTubeSearchRequest
    ): Response<InnerTubeSearchResponse>

    @Headers(
        "Content-Type: application/json",
        "User-Agent: com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; ru_RU; Infinix X6833B)",
        "X-YouTube-Client-Name: 21",
        "X-YouTube-Client-Version: 6.42.52"
    )
    @POST("youtubei/v1/player")
    suspend fun getPlayer(
        @Body request: InnerTubePlayerRequest
    ): Response<InnerTubePlayerResponse>
}
