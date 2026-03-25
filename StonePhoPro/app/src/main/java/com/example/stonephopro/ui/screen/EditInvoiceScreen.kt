package com.example.stonephopro.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.model.Invoice
import com.example.stonephopro.model.Product
import com.example.stonephopro.components.PriceInputDialog
import com.example.stonephopro.components.QuantityInputDialog
@Composable
fun EditInvoiceScreen(
    invoice: Invoice,
    onSave: (Invoice) -> Unit,
    onCancel: () -> Unit
) {
    val editableItems = remember {
        mutableStateListOf<Pair<Product, Int>>().apply {
            invoice.items.groupBy { it }.forEach { (product, list) ->
                add(product to list.size)
            }
        }
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var priceCents by remember { mutableIntStateOf(0) }
    var showQtyDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("✏️ Chỉnh sửa hoá đơn: ${invoice.id}", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(editableItems) { index, (product, quantity) ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${index + 1}. ${product.name}", fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Giá: %.2f$".format(product.price))

                        Button(
                            onClick = {
                                selectedIndex = index
                                showQtyDialog = true
                            },
                            modifier = Modifier.width(80.dp)
                        ) {
                            Text("SL: $quantity")
                        }


                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                selectedIndex = index
                                priceCents = (product.price * 100).toInt()
                                showPriceDialog = true
                            }) {
                                Text("💵 Sửa giá")
                            }

                            Button(onClick = {
                                editableItems.removeAt(index)
                            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("❌ Xoá")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val subtotal = editableItems.sumOf { (product, qty) -> product.price * qty }
        val tax = subtotal * 0.08
        val total = subtotal + tax

        Text("Subtotal: %.2f$".format(subtotal))
        Text("TAX (8%%): %.2f$".format(tax))
        Text("Total: %.2f$".format(total), fontSize = 18.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("❌ Huỷ")
            }
            Button(onClick = {
                val updatedItems = editableItems.flatMap { (product, qty) -> List(qty) { product } }
                val updatedInvoice = invoice.copy(
                    items = updatedItems,
                    subtotal = subtotal,
                    tax = tax,
                    total = total
                )
                onSave(updatedInvoice)
            }, modifier = Modifier.weight(1f)) {
                Text("💾 Lưu")
            }
        }
    }

    if (showPriceDialog && selectedIndex != null) {
        PriceInputDialog(
            initialCents = priceCents,
            onDismiss = { showPriceDialog = false },
            onConfirm = {
                priceCents = it
                val (product, qty) = editableItems[selectedIndex!!]
                val updated = product.copy(price = it / 100.0)
                editableItems[selectedIndex!!] = updated to qty
                showPriceDialog = false
            }
        )
    }
    if (showQtyDialog && selectedIndex != null) {
        val (_, qty) = editableItems[selectedIndex!!]
        QuantityInputDialog(
            initialQty = qty,
            onDismiss = { showQtyDialog = false },
            onConfirm = { newQty ->
                val (product, _) = editableItems[selectedIndex!!]
                editableItems[selectedIndex!!] = product to newQty
                showQtyDialog = false
            }
        )
    }

}
