package com.klischa.ytmusic.data.innertube

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP Клиент для работы с InnerTube API с поддержкой авторизационных заголовков.
 */
class InnerTubeClient(private val context: Context) {

    private val baseUrl = "https://music.youtube.com/"

    private val retryInterceptor = Interceptor { chain ->
        var response: Response? = null
        var exception: IOException? = null
        var tryCount = 0
        val maxLimit = 3

        while (response == null && tryCount < maxLimit) {
            try {
                tryCount++
                response = chain.proceed(chain.request())
            } catch (e: IOException) {
                exception = e
                if (tryCount >= maxLimit) throw e
                try {
                    Thread.sleep(800L * tryCount)
                } catch (ignored: InterruptedException) {}
            }
        }

        response ?: throw exception ?: IOException("Сетевой сбой при обращении к InnerTube API")
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(context))
        .addInterceptor(retryInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val api: InnerTubeApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(InnerTubeApi::class.java)

    companion object {
        @Volatile
        private var instance: InnerTubeClient? = null

        fun getInstance(context: Context): InnerTubeClient {
            return instance ?: synchronized(this) {
                instance ?: InnerTubeClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
