package com.klischa.ytmusic.data.innertube

import android.content.Context
import com.klischa.ytmusic.data.auth.UserAccountManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Перехватчик OkHttp, внедряющий авторизационные Cookie и SAPISIDHASH в запросы к InnerTube API.
 */
class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val accountManager = UserAccountManager.getInstance(context)

        val requestBuilder = original.newBuilder()

        val cookies = accountManager.getSavedCookies()
        if (!cookies.isNullOrEmpty()) {
            requestBuilder.header("Cookie", cookies)
        }

        val authHeader = accountManager.generateSapisidHash()
        if (!authHeader.isNullOrEmpty()) {
            requestBuilder.header("Authorization", authHeader)
            requestBuilder.header("X-Origin", "https://music.youtube.com")
        }

        requestBuilder.header("Origin", "https://music.youtube.com")
        requestBuilder.header("Referer", "https://music.youtube.com/")

        return chain.proceed(requestBuilder.build())
    }
}
