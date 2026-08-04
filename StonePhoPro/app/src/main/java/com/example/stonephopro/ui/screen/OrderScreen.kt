
package com.example.stonephopro.ui.screen

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔐 Nhập mật khẩu", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // PIN display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Color(0xFF1565C0), RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "•".repeat(password.length).ifEmpty { " " },
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A237E),
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Numpad
                    val keys = listOf("1","2","3","4","5","6","7","8","9","⌫","0","✓")
                    val keyColors = mapOf(
                        "⌫" to Pair(Color(0xFFEF5350), Color(0xFFC62828)),
                        "✓" to Pair(Color(0xFF43A047), Color(0xFF1B5E20))
                    )

                    for (row in keys.chunked(3)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (key in row) {
                                val (g1, g2) = keyColors[key] ?: Pair(Color(0xFF1565C0), Color(0xFF0D47A1))
                                Button(
                                    onClick = {
                                        when (key) {
                                            "⌫" -> if (password.isNotEmpty()) password = password.dropLast(1)
                                            "✓" -> {
                                                when (password) {
                                                    "0815" -> { invoicePermission = "admin"; showInvoiceScreen = true }
                                                    "1209" -> { showWeeklyScreen = true }
                                                    else   -> { invoicePermission = "user"; showInvoiceScreen = true }
                                                }
                                                showInvoicePasswordDialog = false
                                                password = ""
                                            }
                                            else -> if (password.length < 6) password += key
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .padding(vertical = 3.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(listOf(g1, g2)),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(0.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { showInvoicePasswordDialog = false; password = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hủy", color = Color.Gray, fontSize = 14.sp)
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