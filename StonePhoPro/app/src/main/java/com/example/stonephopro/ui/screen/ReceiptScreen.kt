package com.example.stonephopro.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.model.Product
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.components.PriceInputDialog
import com.example.stonephopro.utils.PrinterConfig
import com.example.stonephopro.utils.SocketPrinter
import com.example.stonephopro.viewmodel.OrderViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun ReceiptScreen(viewModel: OrderViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var lastInvoiceId by remember { mutableStateOf("") }
    var discountPercent by remember { mutableStateOf(0.0) } // ✅ Discount %
    var showDiscountDialog by remember { mutableStateOf(false) } // ✅ Popup Discount
    val cartItems by remember(viewModel.cart) {
        derivedStateOf {
            viewModel.cart.groupBy { it }.mapValues { it.value.size }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .padding(16.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFfff2cc)
                    )
                )
            )
    ) {
        // Tiêu đề
        Text(
            text = "\uD83D\uDED2 Order Receipt",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Danh sách sản phẩm
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(cartItems.entries.toList()) { (product, quantity) ->
                val totalPrice = product.price * quantity
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${product.name} x$quantity",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!product.note.isNullOrBlank()) {
                                product.note.split("\n").forEach {
                                    Text("  $it", fontSize = 13.sp, color = Color.Gray)

                                }
                            }
                        }
                        Text("%.2f$".format(totalPrice), fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "❌",
                            color = Color.Red,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                // Xoá tất cả quantity món giống nhau
                                repeat(quantity) {
                                    viewModel.removeFromCart(product)

                                }
                            }
                        )
                    }
                }
            }
        }

        // Discount Button và Popup
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(" Discount (%):", fontSize = 14.sp)
            Button(
                onClick = { showDiscountDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
            ) {
                Text("${discountPercent.toInt()}%")
            }
        }

        if (showDiscountDialog) {
            PriceInputDialog(
                initialCents = (discountPercent * 100).toInt(),
                onDismiss = { showDiscountDialog = false },
                onConfirm = { value ->
                    discountPercent = value / 100.0 // ✅ Đổi thành % thực
                    showDiscountDialog = false
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tính toán tổng
        val now = Calendar.getInstance()
        val dateStr = SimpleDateFormat("MM-dd-yyyy", Locale.US).format(now.time)
        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(now.time)

        HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(4.dp))

        val subtotal = cartItems.entries.sumOf { it.key.price * it.value }
        val dis = subtotal * discountPercent / 100
        val taxedSubtotal = subtotal - dis
        val tax = taxedSubtotal * 0.08
        val total = taxedSubtotal + tax

        Text(" Subtotal: %.2f$".format(subtotal), fontSize = 12.sp)
        Text(" Discount (${discountPercent.toInt()}%%): -%.2f$".format(dis), fontSize = 12.sp)
        Text(" TAX 8%%: %.2f$".format(tax), fontSize = 12.sp)
        Text(" Total: %.2f$".format(total), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Save & Print Receipt
        Button3D(
            text = "💾 Save & Print Receipt",
            onClick = {
                val invoiceId = viewModel.saveCurrentInvoice(
                    context = context,
                    subtotal = subtotal,
                    discount = dis,
                    tax = tax,
                    total = total
                )
                val invoiceText = buildPrintableReceipt(
                    invoiceId = invoiceId,
                    cartItems = cartItems,
                    date = dateStr,
                    time = timeStr,
                    subtotal = subtotal,
                    discount = dis,
                    tax = tax,
                    total = total
                )

                scope.launch {
                    val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                    try {
                        SocketPrinter.printText(ip, port, invoiceText)
                        lastInvoiceId = "✅ In thành công: $invoiceId"
                    } catch (e: Exception) {
                        lastInvoiceId = "❌ Lỗi khi in: ${e.message}"
                    }
                    showDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            fontSize = 16.sp,
            gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
        )

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Thông báo") },
                text = { Text(lastInvoiceId) }
            )
        }
    }
}

// ✅ Hàm in hóa đơn hoàn chỉnh
fun buildPrintableReceipt(
    invoiceId: String,
    cartItems: Map<Product, Int>,
    date: String,
    time: String,
    subtotal: Double,
    discount: Double,
    tax: Double,
    total: Double
): String {
    val sb = StringBuilder()
    sb.appendLine("                  STONE PHO POS")
    sb.appendLine("          1525 Baytree Rd, ste M, Valdosta, GA ")
    sb.appendLine("------------------------------------------------")
    sb.appendLine("Invoice ID: $invoiceId")
    sb.appendLine("Date: $date               Time: $time")
    sb.appendLine("------------------------------------------------")

    cartItems.forEach { (product, qty) ->
        val priceStr = "%.2f$".format(product.price * qty)
        sb.appendLine("${product.name.take(28)} x$qty $priceStr")
    }

    sb.appendLine("------------------------------------------------")
    sb.appendLine("Subtotal:                        %.2f$".format(subtotal))
    sb.appendLine("Discount:                      -%.2f$".format(discount))
    sb.appendLine("Tax (8%%):                       %.2f$".format(tax))
    sb.appendLine("TOTAL:                           %.2f$".format(total))
    sb.appendLine("------------------------------------------------")
    sb.appendLine("                  Thank you! \n\n\n")
    return sb.toString()
}
