package com.example.stonephopro.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stonephopro.model.ExtraOption
import com.example.stonephopro.utils.PriceInputPad

@Composable
fun EditExtrasScreen(
    categoryName: String,
    currentExtras: List<ExtraOption>,
    onConfirm: (List<ExtraOption>) -> Unit,
    onCancel: () -> Unit
) {
    val editableExtras = remember { mutableStateListOf(*currentExtras.toTypedArray()) }
    var selectedExtraIndex by remember { mutableStateOf<Int?>(null) }

    var newName by remember { mutableStateOf("") }
    var priceCents by remember { mutableStateOf(0) }
    var confirmDeleteIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("🛠️ Extra cho mục lớn: $categoryName") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Tên Extra") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                PriceInputPad(
                    label = "Giá",
                    value = priceCents,
                    onChange = { priceCents = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (newName.isNotBlank()) {
                            val price = priceCents / 100.0
                            if (selectedExtraIndex != null) {
                                editableExtras[selectedExtraIndex!!] =
                                    ExtraOption(newName.trim(), price)
                                selectedExtraIndex = null
                            } else {
                                editableExtras.add(ExtraOption(newName.trim(), price))
                            }
                            newName = ""
                            priceCents = 0
                        }
                    }) {
                        Text(if (selectedExtraIndex == null) "➕ Thêm" else "💾 Cập nhật")
                    }

                    if (selectedExtraIndex != null) {
                        TextButton(onClick = {
                            selectedExtraIndex = null
                            newName = ""
                            priceCents = 0
                        }) {
                            Text("❌ Bỏ chọn")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn {
                    itemsIndexed(editableExtras) { index, extra ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedExtraIndex = index
                                    newName = extra.name
                                    priceCents = (extra.price * 100).toInt()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(extra.name)
                                Text("${"%.2f".format(extra.price)}$")
                            }

                            Button(
                                onClick = { confirmDeleteIndex = index },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("❌", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(editableExtras.toList())
            }) {
                Text("✔ Lưu tất cả")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("❌ Hủy")
            }
        },
        modifier = Modifier.padding(16.dp)
    )

    if (confirmDeleteIndex != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteIndex = null },
            title = { Text("Xác nhận xoá") },
            text = { Text("Bạn có chắc muốn xoá extra này không?") },
            confirmButton = {
                TextButton(onClick = {
                    editableExtras.removeAt(confirmDeleteIndex!!)
                    confirmDeleteIndex = null
                }) {
                    Text("✔ Xoá")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteIndex = null }) {
                    Text("❌ Hủy")
                }
            }
        )
    }
}
