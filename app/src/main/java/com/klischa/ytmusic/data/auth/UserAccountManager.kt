package com.klischa.ytmusic.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Управляет авторизацией пользователя в YouTube Music, хранением Cookie и генерацией SAPISIDHASH.
 */
class UserAccountManager private constructor(context: Context) {

    private val tag = "UserAccountManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("yt_music_auth_prefs", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userAccountName = MutableStateFlow<String?>(null)
    val userAccountName: StateFlow<String?> = _userAccountName.asStateFlow()

    init {
        val cookies = getSavedCookies()
        _isLoggedIn.value = !cookies.isNullOrEmpty() && (cookies.contains("SAPISID") || cookies.contains("__Secure-3PAPISID"))
        _userAccountName.value = prefs.getString(KEY_USER_NAME, null)
    }

    fun saveCookies(cookies: String, accountName: String? = null) {
        prefs.edit().apply {
            putString(KEY_COOKIES, cookies)
            if (accountName != null) {
                putString(KEY_USER_NAME, accountName)
            }
            apply()
        }
        _isLoggedIn.value = true
        _userAccountName.value = accountName
        Log.i(tag, "Успешно сохранены Cookies сессии YouTube Music")
    }

    fun getSavedCookies(): String? {
        return prefs.getString(KEY_COOKIES, null)
    }

    fun getSapisid(): String? {
        val cookies = getSavedCookies() ?: return null
        val parts = cookies.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("SAPISID=") || trimmed.startsWith("__Secure-3PAPISID=")) {
                val idx = trimmed.indexOf("=")
                if (idx != -1) {
                    return trimmed.substring(idx + 1)
                }
            }
        }
        return null
    }

    /**
     * Генерирует заголовок Authorization: SAPISIDHASH <timestamp>_<sha1(timestamp + " " + SAPISID + " " + origin)>
     */
    fun generateSapisidHash(origin: String = "https://music.youtube.com"): String? {
        val sapisid = getSapisid() ?: return null
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val payload = "$timestamp $sapisid $origin"

        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(payload.toByteArray(Charsets.UTF_8))
            val hexString = digest.joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_$hexString"
        } catch (e: Exception) {
            Log.e(tag, "Ошибка вычисления SAPISIDHASH: ${e.message}")
            null
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _userAccountName.value = null
        Log.i(tag, "Пользователь вышел из аккаунта")
    }

    companion object {
        private const val KEY_COOKIES = "key_yt_cookies"
        private const val KEY_USER_NAME = "key_user_name"

        @Volatile
        private var instance: UserAccountManager? = null

        fun getInstance(context: Context): UserAccountManager {
            return instance ?: synchronized(this) {
                instance ?: UserAccountManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
