package com.example.stonephopro.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.utils.print.KitchenPrinterConfig

@Composable
fun KitchenPrinterSettingsScreen(
    categories: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🍳 Kitchen Printer Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // ── IP section ──
        Text("📡 Địa chỉ máy in", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        KitchenPrinterConfig.Zone.entries.forEach { zone ->
            KitchenIpRow(context, zone)
        }

        HorizontalDivider()

        // ── Category mapping ──
        Text("🗂️ Phân loại → Máy in", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (categories.isEmpty()) {
            Text("(Chưa có danh mục nào)", color = Color.Gray, fontSize = 13.sp)
        }
        categories.forEach { cat ->
            CategoryZoneRow(context, cat)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button3D(
            text = "⬅ Quay lại",
            onClick = onBack,
            modifier = Modifier.height(50.dp),
            fontSize = 16.sp,
            gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
        )
    }
}

@Composable
private fun KitchenIpRow(context: Context, zone: KitchenPrinterConfig.Zone) {
    var text by remember { mutableStateOf(KitchenPrinterConfig.getIpPort(context, zone)) }
    var saved by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${zone.emoji} ${zone.label}", modifier = Modifier.width(72.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false },
            placeholder = { Text("IP:Port", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )
        Button(
            onClick = { KitchenPrinterConfig.setIpPort(context, zone, text); saved = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (saved) Color(0xFF43A047) else Color(0xFF1565C0)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(if (saved) "✓" else "Lưu", fontSize = 12.sp)
        }
    }
}

@Composable
private fun CategoryZoneRow(context: Context, category: String) {
    var selected by remember { mutableStateOf(KitchenPrinterConfig.getCategoryZone(context, category)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(category, modifier = Modifier.weight(1f), fontSize = 13.sp)
        KitchenPrinterConfig.Zone.entries.forEach { zone ->
            val isSelected = selected == zone
            Button(
                onClick = {
                    KitchenPrinterConfig.setCategoryZone(context, category, zone)
                    selected = zone
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF1565C0) else Color(0xFFE0E0E0),
                    contentColor = if (isSelected) Color.White else Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("${zone.emoji}${zone.label}", fontSize = 11.sp)
            }
        }
    }
}
