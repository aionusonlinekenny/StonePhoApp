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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var isLoading          by remember { mutableStateOf(true) }
    var noTokenMsg         by remember { mutableStateOf("") }
    var detectedMerchantId by remember { mutableStateOf("") }
    var manualToken        by remember { mutableStateOf("") }
    var showToken          by remember { mutableStateOf(false) }
    val authUrl = remember { CloverAuthManager.buildAuthUrl() }

    // Xử lý khi bắt được callback URL
    fun handleCallbackUrl(url: String, stopFn: (() -> Unit)? = null): Boolean {
        if (!url.startsWith(CloverAuthManager.REDIRECT_URI)) return false
        stopFn?.invoke()
        val result = CloverAuthManager.parseTokenFromRedirect(url)
        if (result != null) {
            onSuccess(result.first, result.second)
        } else {
            // Có merchant_id nhưng không có token — hiện form nhập token thủ công
            val mid = Uri.parse(url).getQueryParameter("merchant_id") ?: ""
            detectedMerchantId = mid
            noTokenMsg = mid
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
                                        // Token nằm trong fragment (#) — WebView thường bỏ fragment
                                        // khi pass vào shouldOverride/onPageStarted.
                                        // Dùng JS để đọc window.location.href với full fragment.
                                        if (url.contains("stonepho.app") || url.contains("clover/callback")) {
                                            view.evaluateJavascript("window.location.href") { href ->
                                                val fullUrl = href?.trim('"')?.replace("\\u0026", "&") ?: return@evaluateJavascript
                                                handleCallbackUrl(fullUrl) { view.stopLoading() }
                                            }
                                        }
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

                    // Form nhập token thủ công khi OAuth không trả về token
                    if (noTokenMsg.isNotEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🔑", fontSize = 36.sp)
                                Text(
                                    "Nhập API Token của Clover",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    "Merchant ID: $detectedMerchantId",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    fontWeight = FontWeight.Bold
                                )
                                Divider()
                                Text(
                                    "Cách lấy token:\n1. Mở clover.com → đăng nhập nhà hàng\n2. Vào Setup → API Tokens\n3. Bấm New Token → chọn Orders + Inventory\n4. Copy token dán vào đây",
                                    fontSize = 12.sp,
                                    color = Color(0xFF616161),
                                    textAlign = TextAlign.Start
                                )
                                OutlinedTextField(
                                    value = manualToken,
                                    onValueChange = { manualToken = it },
                                    label = { Text("API Token") },
                                    placeholder = { Text("Paste token vào đây") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (showToken)
                                        VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        TextButton(onClick = { showToken = !showToken }) {
                                            Text(if (showToken) "Ẩn" else "Hiện", fontSize = 12.sp)
                                        }
                                    }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Huỷ") }
                                    Button(
                                        onClick = {
                                            if (manualToken.isNotBlank()) {
                                                onSuccess(manualToken.trim(), detectedMerchantId)
                                            }
                                        },
                                        enabled = manualToken.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                    ) { Text("Kết nối", color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
