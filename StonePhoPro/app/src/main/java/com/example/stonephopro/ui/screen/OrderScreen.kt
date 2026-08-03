
package com.example.stonephopro.ui.screen

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.example.stonephopro.viewmodel.OrderViewModel
import com.example.stonephopro.viewmodel.InventoryViewModel
import com.example.stonephopro.utils.print.PrinterSettingsScreen
import com.example.stonephopro.components.Button3D
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun OrderScreen(viewModel: OrderViewModel) {
    val isMenuLoaded by viewModel.isMenuLoaded
    val context = LocalContext.current
    var isReadyToRender by remember { mutableStateOf(false) }
    var readyHeader by remember { mutableStateOf(false) }
    var readyBody by remember { mutableStateOf(false) }
    var readyReceipt by remember { mutableStateOf(false) }
    var showInventoryScreen by remember { mutableStateOf(false) }
    var showCloverScreen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showManageMenu by remember { mutableStateOf(false) }
    var showInvoiceScreen by remember { mutableStateOf(false) }
    var showWeeklyScreen by remember { mutableStateOf(false) }
    var showInvoicePasswordDialog by remember { mutableStateOf(false) }
    var invoicePermission by remember { mutableStateOf("user") }
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler {
        showExitDialog = true
    }
    if (showExitDialog) {
        ExitConfirmationDialog(
            onConfirm = { (context as Activity).finish() },
            onDismiss = { showExitDialog = false }
        )
    }
    LaunchedEffect(isMenuLoaded) {
        if (isMenuLoaded) {
            delay(100); isReadyToRender = true
            delay(100); readyHeader = true
            delay(100); readyBody = true
            delay(100); readyReceipt = true
        }
    }
    if (!isMenuLoaded || !isReadyToRender) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White, // Trắng ở trên
                            Color(0xFFFFD8B2) // Vàng nhạt ở dưới
                        )
                    )
                )
        ) {
            when {
                showSettings -> PrinterSettingsScreen(onBack = { showSettings = false })
                showManageMenu -> ManageMenuScreen(viewModel = viewModel, onBack = { showManageMenu = false })
                showWeeklyScreen -> WeeklyIncomeScreen(
                    onBack = { showWeeklyScreen = false }
                )
                showInvoiceScreen -> InvoiceHistoryScreen(
                    onBack = { showInvoiceScreen = false },
                    permission = invoicePermission
                )
                showInventoryScreen -> InventoryScreen(
                    viewModel = remember { InventoryViewModel(context) },
                    onBack = { showInventoryScreen = false },
                )
                showCloverScreen -> CloverOrderScreen(
                    onBack = { showCloverScreen = false }
                )
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (readyHeader) {
                            HeaderScreen(
                                viewModel = viewModel,
                                onManageMenu = { showManageMenu = true },
                                onSettings = { showSettings = true },
                                onRequireInvoicePassword = { showInvoicePasswordDialog = true },
                                onOpenInventory = { showInventoryScreen = true },
                                onOpenClover = { showCloverScreen = true }
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.weight(0.7f)) {
                                if (readyBody) {
                                    MenuBodyButton(viewModel)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    BodyScreen(viewModel)
                                }
                            }
                            if (readyReceipt) {
                                ReceiptScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }


    // ✅ Popup nhập mật khẩu phân quyền
    if (showInvoicePasswordDialog) {
        var password by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showInvoicePasswordDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("🔐 Nhập mật khẩu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "👉 Nhập bất kỳ để đăng nhập với quyền xem. Liên hệ Kenny để có quyền quản trị.",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button3D(
                            text = "❌ Hủy",
                            onClick = {
                                showInvoicePasswordDialog = false
                            },
                            gradientColors = listOf(Color(0xFFE57373), Color(0xFFD32F2F)), // Gradient đỏ
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button3D(
                            text = "✔ OK",
                            onClick = {
                                when (password) {
                                    "0815" -> { invoicePermission = "admin"; showInvoiceScreen = true }
                                    "1209" -> { showWeeklyScreen = true }
                                    else   -> { invoicePermission = "user"; showInvoiceScreen = true }
                                }
                                showInvoicePasswordDialog = false
                            },
                            gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)), // Gradient xanh lá
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun ExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thoát ứng dụng") },
        text = { Text("Bạn có chắc chắn muốn thoát ứng dụng không?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Thoát")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}