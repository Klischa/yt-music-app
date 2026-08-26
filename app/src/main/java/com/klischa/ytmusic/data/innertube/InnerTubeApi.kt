package com.klischa.ytmusic.data.innertube

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface InnerTubeApi {

    @Headers(
        "Content-Type: application/json",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Origin: https://music.youtube.com",
        "Referer: https://music.youtube.com/",
        "X-YouTube-Client-Name: 67",
        "X-YouTube-Client-Version: 1.20240401.01.00"
    )
    @POST("youtubei/v1/search")
    suspend fun search(
        @Body request: InnerTubeSearchRequest
    ): Response<JsonObject>

    @Headers(
        "Content-Type: application/json",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Origin: https://music.youtube.com",
        "Referer: https://music.youtube.com/",
        "X-YouTube-Client-Name: 67",
        "X-YouTube-Client-Version: 1.20240401.01.00"
    )
    @POST("youtubei/v1/player")
    suspend fun getPlayer(
        @Body request: InnerTubePlayerRequest
    ): Response<JsonObject>
}
