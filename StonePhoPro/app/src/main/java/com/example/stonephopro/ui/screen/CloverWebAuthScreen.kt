package com.example.stonephopro.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog nhập Merchant ID + API Token thủ công.
 * Không dùng WebView OAuth (quá phức tạp với fragment redirect).
 * Token lấy từ: clover.com → Setup → API Tokens → New Token
 */
@Composable
fun CloverWebAuthDialog(
    onSuccess: (token: String, merchantId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var merchantId by remember { mutableStateOf("") }
    var apiToken   by remember { mutableStateOf("") }
    var showToken  by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .wrapContentHeight()
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔐", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Kết nối Clover POS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Nhập thông tin từ clover.com",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Divider()

                // Hướng dẫn
                Surface(
                    color = Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📋 Cách lấy thông tin:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("• Merchant ID: Clover device → Settings → About", fontSize = 11.sp)
                        Text("• API Token: clover.com → Setup → API Tokens → New Token", fontSize = 11.sp)
                        Text("  (chọn quyền: Orders + Inventory)", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                // Merchant ID
                OutlinedTextField(
                    value = merchantId,
                    onValueChange = { merchantId = it.trim() },
                    label = { Text("Merchant ID") },
                    placeholder = { Text("Vd: 04VMDMMGF5K81") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // API Token
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it.trim() },
                    label = { Text("API Token") },
                    placeholder = { Text("Paste token vào đây") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(if (showToken) "Ẩn" else "Hiện", fontSize = 12.sp)
                        }
                    }
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Huỷ")
                    }
                    Button(
                        onClick = { onSuccess(apiToken, merchantId) },
                        enabled = merchantId.isNotBlank() && apiToken.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Text("✅ Kết nối", color = Color.White)
                    }
                }

                Text(
                    "Token được lưu trên máy, không gửi lên server nào.",
                    fontSize = 10.sp,
                    color = Color(0xFF9E9E9E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
