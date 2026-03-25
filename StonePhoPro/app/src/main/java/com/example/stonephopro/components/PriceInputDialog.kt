package com.example.stonephopro.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.utils.PriceInputPad

@Composable
fun PriceInputDialog(
    initialCents: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val priceCents = remember { mutableStateOf(initialCents) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(priceCents.value) }) {
                Text("✔ OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("❌ Hủy")
            }
        },
        title = { Text("Nhập giá") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PriceInputPad(
                    label = "Giá tiền",
                    value = priceCents.value,
                    onChange = { priceCents.value = it }
                )
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun QuantityInputDialog(
    initialQty: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantity by remember { mutableIntStateOf(initialQty) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(quantity) }) {
                Text("✔ OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("❌ Hủy")
            }
        },
        title = { Text("Nhập số lượng") },
        text = {
            Column {
                OutlinedTextField(
                    value = quantity.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Số lượng") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("←", "0", "AC")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            Button3D(
                                text = digit,
                                onClick = {
                                    quantity = when (digit) {
                                        "←" -> quantity / 10
                                        "AC" -> 0
                                        else -> (quantity * 10 + digit.toInt()).coerceAtMost(999)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .height(50.dp), // ✅ chiều cao phù hợp
                                fontSize = 18.sp, // ✅ chữ to hơn cho dễ đọc
                                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient màu xanh dương
                            )

                        }
                    }
                }
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}
