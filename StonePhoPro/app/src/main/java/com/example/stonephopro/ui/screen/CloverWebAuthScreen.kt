package com.example.stonephopro.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.text.style.TextAlign
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
    var isLoading       by remember { mutableStateOf(true) }
    var noTokenMsg      by remember { mutableStateOf("") }
    val authUrl = remember { CloverAuthManager.buildAuthUrl() }

    // Xử lý khi bắt được callback URL (dùng chung cho cả onPageStarted và shouldOverride)
    fun handleCallbackUrl(url: String, stopFn: (() -> Unit)? = null): Boolean {
        if (!url.startsWith(CloverAuthManager.REDIRECT_URI)) return false
        stopFn?.invoke()
        val result = CloverAuthManager.parseTokenFromRedirect(url)
        if (result != null) {
            onSuccess(result.first, result.second)
        } else {
            // Redirect OK nhưng không có token — merchant cần disconnect rồi reconnect
            val mid = Uri.parse(url).getQueryParameter("merchant_id") ?: ""
            noTokenMsg = "Clover kết nối thành công nhưng chưa trả token.\n\n" +
                "Vui lòng:\n1. Vào Clover App Market\n2. Tìm StonePhoApp\n3. Bấm Disconnect\n4. Bấm Connect lại\n\n" +
                if (mid.isNotEmpty()) "Merchant ID: $mid" else ""
        }
        return true
    }

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
                                    // Chặn redirect TRƯỚC khi WebView load
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean {
                                        val url = request.url.toString()
                                        if (handleCallbackUrl(url)) return true
                                        return false
                                    }

                                    // Backup: bắt ngay khi page bắt đầu load (trường hợp shouldOverride bị bỏ qua)
                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        if (handleCallbackUrl(url) { view.stopLoading() }) return
                                        isLoading = true
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

                    // Hiện khi kết nối được nhưng không có token
                    if (noTokenMsg.isNotEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text("⚠️", fontSize = 48.sp)
                                Text(
                                    noTokenMsg,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    color = Color(0xFF424242)
                                )
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                ) {
                                    Text("Đóng", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
