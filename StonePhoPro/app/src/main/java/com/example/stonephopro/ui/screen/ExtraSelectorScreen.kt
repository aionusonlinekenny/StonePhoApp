package com.example.stonephopro.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.example.stonephopro.model.ExtraOption
import com.example.stonephopro.model.Product
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.utils.ExtraStorage

@Composable
fun ExtraSelectorScreen(
    product: Product,
    onConfirm: (Product) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Tên file: lowercase mục lớn
    val listName = product.category.lowercase().replace(" ", "_") + "_extras"

    // Load extra ban đầu từ file
    val editableExtras = remember {
        mutableStateListOf<ExtraOption>().apply {
            val loaded = ExtraStorage.loadExtras(context, listName)
            addAll(loaded)
        }
    }

    val selectedExtras = remember { mutableStateListOf<ExtraOption>() }
    var showEditScreen by remember { mutableStateOf(false) }

    if (showEditScreen) {
        ManageExtrasScreen(
            categoryName = product.category,
            currentExtras = editableExtras.toList(),
            onBack = { showEditScreen = false },
            onSave = { newList ->
                editableExtras.clear()
                editableExtras.addAll(newList)
                ExtraStorage.saveExtras(context, listName, newList)
                showEditScreen = false
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🧾 ${product.name}", fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(editableExtras) { option ->
                val isSelected = selectedExtras.contains(option)
                Button(
                    onClick = {
                        if (isSelected) selectedExtras.remove(option)
                        else selectedExtras.add(option)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("${option.name} (${String.format("%.2f", option.price)}$)", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button3D(
                text = "⬅ Quay lại",
                onClick = onBack,
                modifier = Modifier
                    .height(50.dp) // ✅ cao hơn, bạn có thể điều chỉnh
                    .fillMaxWidth(0.3f), // ✅ rộng 30% màn hình, tùy chỉnh
                fontSize = 18.sp, // ✅ chữ to hơn
                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient màu xanh
            )

            TextButton(onClick = { showEditScreen = true }) {
                Text("➕ Thêm/Chỉnh sửa Extra")
            }
            Button3D(
                text = "✔ OK",
                onClick = {
                    val newPrice = product.price + selectedExtras.sumOf { it.price }
                    val note = selectedExtras.joinToString("\n") {
                        "+ ${it.name}: ${"%.2f".format(it.price)}$"
                    }
                    val newProduct = product.copy(
                        name = product.name + if (selectedExtras.isNotEmpty()) " + Extra" else "",
                        price = newPrice,
                        note = note
                    )
                    onConfirm(newProduct)
                },
                modifier = Modifier
                    .height(50.dp) // ✅ cao hơn
                    .fillMaxWidth(0.4f), // ✅ rộng 40% màn hình, bạn có thể đổi thành .fillMaxWidth() cho 100%
                fontSize = 18.sp, // ✅ chữ to hơn
                gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // ✅ Gradient màu xanh đậm
            )

        }
    }
}
