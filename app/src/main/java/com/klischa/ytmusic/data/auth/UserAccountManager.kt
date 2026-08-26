package com.klischa.ytmusic.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Управляет авторизацией пользователя в YouTube Music, сессионными Cookie и криптографической подписью SAPISIDHASH.
 */
class UserAccountManager private constructor(private val context: Context) {

    private val tag = "UserAccountManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("yt_music_auth_prefs", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userAccountName = MutableStateFlow<String?>(null)
    val userAccountName: StateFlow<String?> = _userAccountName.asStateFlow()

    init {
        checkLoginStatus()
    }

    fun checkLoginStatus() {
        val cookies = getSavedCookies()
        val hasAuthCookie = !cookies.isNullOrEmpty() && (
            cookies.contains("SAPISID") ||
            cookies.contains("__Secure-3PAPISID") ||
            cookies.contains("__Secure-1PAPISID") ||
            cookies.contains("LOGIN_INFO") ||
            cookies.contains("SID")
        )
        _isLoggedIn.value = hasAuthCookie
        _userAccountName.value = prefs.getString(KEY_USER_NAME, if (hasAuthCookie) "Google Аккаунт" else null)
    }

    fun saveCookies(cookies: String, accountName: String? = null) {
        prefs.edit().apply {
            putString(KEY_COOKIES, cookies)
            if (accountName != null) {
                putString(KEY_USER_NAME, accountName)
            }
            apply()
        }
        checkLoginStatus()
        Log.i(tag, "Сохранены Cookies авторизации YouTube Music (длина: ${cookies.length})")
    }

    fun getSavedCookies(): String? {
        val saved = prefs.getString(KEY_COOKIES, null)
        if (!saved.isNullOrEmpty()) return saved

        return try {
            val cm = CookieManager.getInstance()
            val ytm = cm.getCookie("https://music.youtube.com") ?: ""
            val yt = cm.getCookie("https://www.youtube.com") ?: ""
            val g = cm.getCookie("https://accounts.google.com") ?: ""
            val combined = "$ytm; $yt; $g".trim().trim(';').trim()
            if (combined.isNotEmpty()) combined else null
        } catch (e: Exception) {
            null
        }
    }

    fun getSapisid(): String? {
        val cookies = getSavedCookies() ?: return null
        val parts = cookies.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("SAPISID=") ||
                trimmed.startsWith("__Secure-3PAPISID=") ||
                trimmed.startsWith("__Secure-1PAPISID=")
            ) {
                val idx = trimmed.indexOf("=")
                if (idx != -1) {
                    val value = trimmed.substring(idx + 1).trim()
                    if (value.isNotEmpty()) return value
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
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (ignored: Exception) {}
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
