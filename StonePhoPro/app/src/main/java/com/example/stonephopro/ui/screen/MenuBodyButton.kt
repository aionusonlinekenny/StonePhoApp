package com.example.stonephopro.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stonephopro.viewmodel.OrderViewModel
import androidx.compose.ui.graphics.Color
import com.example.stonephopro.components.Button3D

@Composable
fun MenuBodyButton(viewModel: OrderViewModel) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(viewModel.categories) { category ->
            val isSelected = viewModel.selectedCategory.value.name == category.name

            Button3D(
                text = category.name,
                onClick = { viewModel.selectedCategory.value = category },
                gradientColors = if (isSelected) {
                    listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // Gradient xanh lá khi chọn
                } else {
                    val colorHex = Color(android.graphics.Color.parseColor(category.colorHex))
                    listOf(colorHex, colorHex) // Màu đơn khi không chọn
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

