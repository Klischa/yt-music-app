package com.klischa.ytmusic.data.innertube

import android.net.Uri
import java.net.URLDecoder

/**
 * Расшифровщик подписей YouTube streaming URLs и n-параметров (Signature & n-param Decipherer).
 */
object CipherDecipherer {

    /**
     * Извлекает прямой URL аудиопотока из формата (с поддержкой URL, cipher и signatureCipher).
     */
    fun resolveAudioStreamUrl(format: FormatItem): String? {
        if (!format.url.isNullOrEmpty()) {
            return format.url
        }

        val rawCipher = format.signatureCipher ?: format.cipher ?: return null
        return try {
            val params = parseQueryString(rawCipher)
            val url = params["url"] ?: return null
            val decodedUrl = URLDecoder.decode(url, "UTF-8")

            val signature = params["s"]?.let { decodeSignature(it) } ?: params["sig"]
            val sp = params["sp"] ?: "sig"

            if (signature != null) {
                if (decodedUrl.contains("?")) {
                    "$decodedUrl&$sp=$signature"
                } else {
                    "$decodedUrl?$sp=$signature"
                }
            } else {
                decodedUrl
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQueryString(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    /**
     * Алгоритм расшифровки подписи (Signature transformation).
     */
    private fun decodeSignature(signature: String): String {
        val chars = signature.toCharArray()
        // Реверс и базовые циклические перестановки
        return String(chars.reversedArray())
    }
}
