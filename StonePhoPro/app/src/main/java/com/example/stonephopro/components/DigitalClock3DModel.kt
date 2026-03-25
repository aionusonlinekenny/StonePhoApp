package com.example.stonephopro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DigitalClock3DModel() {
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    var currentDate by remember { mutableStateOf(getCurrentDate()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            currentDate = getCurrentDate()
            delay(1000L) // Cập nhật mỗi giây
        }
    }

    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = Modifier

            .padding(6.dp)
            .background(Color.Transparent) // Nền trong suốt
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .width(200.dp) // Giới hạn chiều rộng 240px
                .padding(4.dp)
        ) {
            // Ngày tháng năm với màu trắng
            Text(
                text = currentDate,
                style = TextStyle(
                    fontSize = 12.sp, // Đảm bảo là TextUnit (sp)
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = Color.Gray,
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )

            // Giờ phút giây với Gradient Text
            GradientText(
                text = currentTime,
                fontSize = 22.sp // Đã sửa thành TextUnit (sp)
            )
        }
    }
}

@Composable
fun GradientText(
    text: String,
    fontSize: TextUnit // Đã sửa từ Int thành TextUnit
) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            brush = Brush.linearGradient(
                colors = listOf(Color.Magenta, Color.Cyan)
            ),
            shadow = Shadow(
                color = Color.Gray,
                offset = Offset(4f, 4f),
                blurRadius = 6f
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    )
}

// Hàm lấy thời gian hiện tại (12 giờ - AM/PM)
fun getCurrentTime(): String {
    val dateFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    return dateFormat.format(Date())
}

// Hàm lấy ngày hiện tại
fun getCurrentDate(): String {
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    return dateFormat.format(Date())
}

@Preview(showBackground = true)
@Composable
fun PreviewDigitalClock3DModel() {
    DigitalClock3DModel()
}