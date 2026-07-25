package com.example.stonephopro.ui.screen

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stonephopro.utils.clover.CloverAuthManager

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloverWebAuthDialog(
    onSuccess: (token: String, merchantId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    val authUrl = remember { CloverAuthManager.buildAuthUrl() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A237E))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🔐 Đăng nhập Clover",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    TextButton(onClick = onDismiss) {
                        Text("✕ Đóng", color = Color.White)
                    }
                }

                // WebView
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36"

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean {
                                        val url = request.url.toString()
                                        // Bắt redirect về stonepho.app/clover/callback
                                        val result = CloverAuthManager.parseTokenFromRedirect(url)
                                        if (result != null) {
                                            val (token, merchantId) = result
                                            onSuccess(token, merchantId)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        isLoading = false
                                    }
                                }
                                loadUrl(authUrl)
                            }
                        }
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF1A237E))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Đang tải trang đăng nhập Clover...")
                            }
                        }
                    }
                }
            }
        }
    }
}
