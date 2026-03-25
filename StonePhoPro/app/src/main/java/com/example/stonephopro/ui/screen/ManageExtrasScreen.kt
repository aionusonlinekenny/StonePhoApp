package com.example.stonephopro.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.model.ExtraOption
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.components.PriceInputDialog

@Composable
fun ManageExtrasScreen(
    categoryName: String,
    currentExtras: List<ExtraOption>,
    onBack: () -> Unit,
    onSave: (List<ExtraOption>) -> Unit
) {
    val editableExtras = remember { mutableStateListOf(*currentExtras.toTypedArray()) }
    var selectedExtraIndex by remember { mutableStateOf<Int?>(null) }

    var newName by remember { mutableStateOf("") }
    var priceCents by remember { mutableStateOf(0) }
    var confirmDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var showPriceDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button3D(
                text = "⬅ Quay lại",
                onClick = onBack,
                modifier = Modifier
                    .height(50.dp)
                    .weight(1f), // Chia đều cho hai nút
                fontSize = 16.sp,
                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // Gradient xanh dương
            )

            Button3D(
                text = "💾 Lưu tất cả",
                onClick = { onSave(editableExtras.toList()) },
                modifier = Modifier
                    .height(50.dp)
                    .weight(1f), // Chia đều cho hai nút
                fontSize = 16.sp,
                gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // Gradient xanh lá đậm
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("🔧 Extra cho mục: $categoryName", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Tên Extra") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPriceDialog = true }
        ) {
            OutlinedTextField(
                value = "%.2f".format(priceCents / 100.0),
                onValueChange = {},
                readOnly = true,
                label = { Text("Giá") },
                enabled = false, // tránh nhầm lẫn người dùng cố gõ vào
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button3D(
                text = if (selectedExtraIndex == null) "➕ Thêm" else "💾 Cập nhật",
                onClick = {
                    if (newName.isNotBlank()) {
                        val price = priceCents / 100.0
                        if (selectedExtraIndex != null) {
                            editableExtras[selectedExtraIndex!!] = ExtraOption(newName.trim(), price)
                            selectedExtraIndex = null
                        } else {
                            editableExtras.add(ExtraOption(newName.trim(), price))
                        }
                        newName = ""
                        priceCents = 0
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp), // ✅ chiều cao dễ bấm
                fontSize = 16.sp, // ✅ chữ to hơn cho dễ đọc
                gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // ✅ Gradient màu xanh lá đậm
            )


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

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp) // tránh dính nút bấm
            ) {
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
                            Text("%.2f$".format(extra.price), fontSize = 13.sp)
                        }
                        Button(
                            onClick = { confirmDeleteIndex = index },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("❌", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

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

        if (showPriceDialog) {
            PriceInputDialog(
                initialCents = priceCents,
                onDismiss = { showPriceDialog = false },
                onConfirm = {
                    priceCents = it
                    showPriceDialog = false
                }
            )
        }
    }
}
