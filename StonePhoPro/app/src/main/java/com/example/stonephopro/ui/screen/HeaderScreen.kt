package com.example.stonephopro.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.viewmodel.OrderViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.example.stonephopro.utils.PriceInputPad
import androidx.compose.runtime.mutableIntStateOf
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.components.DigitalClock3DModel
import com.example.stonephopro.components.AppLogo

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun HeaderScreen(
    viewModel: OrderViewModel,
    onManageMenu: () -> Unit,
    onSettings: () -> Unit,
    onOpenInventory: () -> Unit,
    onRequireInvoicePassword: () -> Unit // ✅ callback để mở popup nhập mật khẩu ở OrderScreen
) {
    var showMenu by remember { mutableStateOf(false) }
    var showQuickPayDialog by remember { mutableStateOf(false) }
    var quickPayName by remember { mutableStateOf(TextFieldValue()) }
    val priceCents = remember { mutableIntStateOf(0) } // ✅ Thêm tại đây
    val now = remember { Date() }
    val dateStr = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US).format(now) }
    val timeStr = remember { SimpleDateFormat("HH:mm", Locale.US).format(now) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☰", fontSize = 24.sp, modifier = Modifier.clickable { showMenu = true })
            Spacer(modifier = Modifier.width(8.dp))
            AppLogo()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button3D(
                text = "💳 Quick Pay",
                onClick = {
                    quickPayName = TextFieldValue("Table_") // ✅ Gán tên mặc định
                    showQuickPayDialog = true
                },
                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // Màu xanh gradient
            )

            Button3D(
                text = "🛒 Product",
                onClick = { onManageMenu() },
                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // Xanh gradient
            )

            Button3D(
                text = "🧾 Invoice Check",
                onClick = { onRequireInvoicePassword() },
                gradientColors = listOf(Color(0xFFFFA726), Color(0xFFFF7043)) // Cam gradient
            )

            DigitalClock3DModel()
        }


        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        "📁 Quản lý sản phẩm",
                        fontSize = 22.sp // ✅ chữ to hơn
                    )
                },
                modifier = Modifier.height(50.dp),
                onClick = {
                onManageMenu()
                showMenu = false
            })
            DropdownMenuItem(
                text = { Text("🧾 Kiểm tra hóa đơn", fontSize = 22.sp) },
                modifier = Modifier.height(56.dp),
                onClick = {
                    onRequireInvoicePassword()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("📦 Inventory", fontSize = 22.sp) },
                onClick = {
                    onOpenInventory()
                    showMenu = false
                }
            )

            DropdownMenuItem(
                text = { Text("⚙️ Cài đặt in", fontSize = 22.sp) },
                modifier = Modifier.height(56.dp),
                onClick = {
                    onSettings()
                    showMenu = false
                }
            )

        }
    }

    if (showQuickPayDialog) {
        AlertDialog(
            onDismissRequest = { showQuickPayDialog = false },
            title = { Text("💳 Quick Pay") },
            text = {
                Column {
                    OutlinedTextField(
                        value = quickPayName,
                        onValueChange = { quickPayName = it },
                        label = { Text("Tên sản phẩm") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))


                    PriceInputPad(
                        label = "Giá tiền",
                        value = priceCents.intValue,
                        onChange = { priceCents.intValue = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = quickPayName.text.trim()
                    val price = priceCents.intValue / 100.0
                    if (name.isNotEmpty() && price > 0.0) {
                        viewModel.addCustomProduct(name, price)
                    }
                    quickPayName = TextFieldValue()
                    priceCents.intValue = 0
                    showQuickPayDialog = false
                }) {
                    Text("Thêm vào thanh toán")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    quickPayName = TextFieldValue()
                    priceCents.intValue = 0
                    showQuickPayDialog = false
                }) {
                    Text("Hủy")
                }
            }
        )
    }
}
