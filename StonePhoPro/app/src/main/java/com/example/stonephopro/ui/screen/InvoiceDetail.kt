package com.example.stonephopro.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
            .padding(16.dp)
    ) {
        // Top buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) {
                Text("⬅ Quay lại")
            }
            if (isAdmin) {
                Button(
                    onClick = { onEdit(invoice) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("✏️ Chỉnh sửa")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Receipt card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Text(
                    "STONE PHO POS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "1525 Baytree Rd, ste M, Valdosta, GA",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                ReceiptDivider()

                // Invoice meta
                ReceiptRow("Invoice ID:", invoice.id)
                if (!invoice.tableTitle.isNullOrBlank())
                    ReceiptRow("Table:", invoice.tableTitle, labelBold = true, valueBold = true)
                ReceiptRow("Date:", invoice.date)
                ReceiptRow("Time:", invoice.time)

                ReceiptDivider()

                // Items
                groupedItems.forEach { (product, qty) ->
                    val lineTotal = product.price * qty
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${product.name} x$qty",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%.2f$".format(lineTotal),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                ReceiptDivider()

                // Totals
                ReceiptRow("Subtotal:", "%.2f$".format(invoice.subtotal))
                if (invoice.discount > 0)
                    ReceiptRow("Discount:", "-%.2f$".format(invoice.discount))
                ReceiptRow("Tax (8%):", "%.2f$".format(invoice.tax))

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TOTAL:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "%.2f$".format(invoice.total),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                ReceiptDivider()

                Text(
                    "Thank you!",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ReceiptDivider() {
    Text(
        "------------------------------------------------",
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = Color.Gray,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    labelBold: Boolean = false,
    valueBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
