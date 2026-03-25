package com.example.stonephopro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text

@Composable
fun Button3D(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    gradientColors: List<Color>? = listOf(Color(0xFF87CEFA), Color(0xFF4682B4)), // nếu null thì dùng solid
    solidColor: Color? = null
) {
    val backgroundModifier = if (gradientColors != null) {
        Modifier.background(
            brush = Brush.linearGradient(gradientColors),
            shape = RoundedCornerShape(24.dp)
        )
    } else {
        Modifier.background(
            color = solidColor ?: Color.Gray,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(24.dp))
            .then(backgroundModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            color = Color.White
        )
    }
}

@Composable
fun FilterButton3D(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorSelected = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))  // Xanh khi chọn
    val colorUnselected = listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)) // Xám khi chưa chọn

    Button3D(
        text = text,
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 50.dp),
        fontSize = 13.sp,
        gradientColors = if (selected) colorSelected else colorUnselected
    )
}
