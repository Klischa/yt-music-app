package com.klischa.ytmusic.presentation.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.klischa.ytmusic.data.auth.UserAccountManager
import com.klischa.ytmusic.presentation.ui.theme.DarkBackground
import com.klischa.ytmusic.presentation.ui.theme.RedPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextPrimary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val accountManager = remember { UserAccountManager.getInstance(context) }
    var isLoading by remember { mutableStateOf(true) }

    val loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&continue=https%3A%2F%2Fmusic.youtube.com%2F"

    fun syncAndSaveCookies(): Boolean {
        val cm = CookieManager.getInstance()
        val ytm = cm.getCookie("https://music.youtube.com") ?: ""
        val yt = cm.getCookie("https://www.youtube.com") ?: ""
        val g = cm.getCookie("https://accounts.google.com") ?: ""
        val combined = "$ytm; $yt; $g".trim().trim(';').trim()

        if (combined.isNotEmpty() && (combined.contains("SAPISID") || combined.contains("__Secure-3PAPISID") || combined.contains("LOGIN_INFO") || combined.contains("SID"))) {
            accountManager.saveCookies(combined, "YouTube Music Аккаунт")
            return true
        }
        return false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = TextPrimary
                )
            }

            Text(
                text = "Вход в YouTube Music",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            Button(
                onClick = {
                    if (syncAndSaveCookies()) {
                        Toast.makeText(context, "Авторизация сохранена!", Toast.LENGTH_SHORT).show()
                        onLoginSuccess()
                    } else {
                        Toast.makeText(context, "Пожалуйста, завершите вход в аккаунт", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text("Готово")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Infinix X6833B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                if (syncAndSaveCookies()) {
                                    onLoginSuccess()
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                if (syncAndSaveCookies()) {
                                    Toast.makeText(ctx, "Авторизация успешна! Доступ к аудиопотокам открыт.", Toast.LENGTH_LONG).show()
                                    onLoginSuccess()
                                }
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl(loginUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            }
        }
    }
}
