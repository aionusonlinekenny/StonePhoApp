package com.example.stonephopro.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.model.Invoice

@Composable
fun InvoiceDetail(
    invoice: Invoice,
    onBack: () -> Unit,
    isAdmin: Boolean,
    onEdit: (Invoice) -> Unit
) {
    val groupedItems = invoice.items.groupBy { it }.mapValues { it.value.size }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) {
            Text("⬅ Quay lại")
        }
        if (isAdmin) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onEdit(invoice) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("✏️ Chỉnh sửa hoá đơn")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("🧾 Chi tiết hóa đơn", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text("🆔 Order ID: ${invoice.id}", fontWeight = FontWeight.Bold)
        Text("📅 Ngày: ${invoice.date}")
        Text("🕒 Giờ: ${invoice.time}")

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("Danh sách sản phẩm:", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))

        groupedItems.forEach { (product, qty) ->
            val totalPrice = product.price * qty
            Text("- ${product.name} x$qty = ${"%.2f".format(totalPrice)}$", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("Subtotal: ${"%.2f".format(invoice.subtotal)}$")
        Text("Thuế (8%): ${"%.2f".format(invoice.tax)}$")
        Text("Tổng cộng: ${"%.2f".format(invoice.total)}$", fontWeight = FontWeight.Bold)
    }
}
