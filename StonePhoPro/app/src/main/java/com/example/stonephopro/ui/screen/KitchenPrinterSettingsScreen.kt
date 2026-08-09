package com.example.stonephopro.ui.screen

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
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
import com.example.stonephopro.utils.print.scanLANForPrinters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun wifiOk(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

@Composable
fun KitchenPrinterSettingsScreen(
    categories: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val discoveredPrinters = remember { mutableStateListOf<String>() }
    var isScanning   by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanStatus   by remember { mutableStateOf("") }
    var scanJob      by remember { mutableStateOf<Job?>(null) }
    // tracks which zone a discovered printer was last assigned to (for ✓ feedback)
    val assignedTo   = remember { mutableStateMapOf<String, KitchenPrinterConfig.Zone>() }
    // bump this to force KitchenIpRow to re-read SharedPreferences after a scan-assign
    var ipRefreshKey by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🍳 Kitchen Printer Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // ── Scan section ──────────────────────────────────────────────────────
        Text("📡 Tìm máy in trong mạng", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        if (!wifiOk(context)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFE082), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚠️ Chưa kết nối WiFi! Kết nối WiFi cùng mạng với máy in trước khi tìm kiếm.",
                    fontSize = 12.sp, color = Color(0xFF5D4037)
                )
            }
        }

        Button3D(
            text = if (isScanning) "🔍 Đang quét..." else "🔍 Scan tìm máy in",
            onClick = {
                if (isScanning) {
                    scanJob?.cancel()
                    isScanning = false
                    scanStatus = "❌ Đã dừng quét."
                    return@Button3D
                }
                if (!wifiOk(context)) {
                    scanStatus = "⚠️ Chưa kết nối WiFi!"
                    return@Button3D
                }
                isScanning = true
                scanProgress = 0f
                discoveredPrinters.clear()
                assignedTo.clear()
                scanStatus = "🔄 Đang quét..."
                scanJob = scope.launch(Dispatchers.IO) {
                    scanLANForPrinters(
                        onPrinterFound = { discoveredPrinters.add(it) },
                        onProgressUpdate = { p, ip ->
                            scanProgress = p
                            scanStatus = "🔄 Đang quét: $ip  (${discoveredPrinters.size} tìm thấy)"
                        },
                        timeout = 500
                    )
                    withContext(Dispatchers.Main) {
                        isScanning = false
                        scanStatus = if (discoveredPrinters.isEmpty())
                            "⚠️ Không tìm thấy máy in nào"
                        else
                            "✅ Tìm thấy ${discoveredPrinters.size} máy in — Tap để gán"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            fontSize = 15.sp,
            gradientColors = if (isScanning)
                listOf(Color(0xFF546E7A), Color(0xFF263238))
            else
                listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))
        )

        if (isScanning) {
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }

        if (scanStatus.isNotEmpty()) {
            Text(scanStatus, fontSize = 12.sp, color = Color(0xFF616161))
        }

        // Discovered printers — tap a zone button to assign
        if (discoveredPrinters.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                discoveredPrinters.forEach { printer ->
                    val ip = printer.removePrefix("TCP:").substringBeforeLast(":").ifEmpty { printer }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "🖨️ $ip",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        KitchenPrinterConfig.Zone.entries.forEach { zone ->
                            val isAssigned = assignedTo[printer] == zone
                            Button(
                                onClick = {
                                    KitchenPrinterConfig.setIpPort(context, zone, printer)
                                    assignedTo[printer] = zone
                                    ipRefreshKey++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAssigned) Color(0xFF43A047) else Color(0xFF1565C0)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    if (isAssigned) "✓ ${zone.label}" else "${zone.emoji} ${zone.label}",
                                    fontSize = 11.sp, color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Manual IP input per zone ──────────────────────────────────────────
        Text("✏️ Địa chỉ máy in (thủ công)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        KitchenPrinterConfig.Zone.entries.forEach { zone ->
            KitchenIpRow(context = context, zone = zone, refreshKey = ipRefreshKey)
        }

        HorizontalDivider()

        // ── Category mapping ──────────────────────────────────────────────────
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
            onClick = {
                scanJob?.cancel()
                onBack()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            fontSize = 16.sp,
            gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
        )
    }
}

@Composable
private fun KitchenIpRow(context: Context, zone: KitchenPrinterConfig.Zone, refreshKey: Int = 0) {
    // remember(refreshKey) → re-reads SharedPreferences whenever a scan assigns a printer
    var text  by remember(refreshKey) { mutableStateOf(KitchenPrinterConfig.getIpPort(context, zone)) }
    var saved by remember(refreshKey) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "${zone.emoji} ${zone.label}",
            modifier = Modifier.width(72.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false },
            placeholder = { Text("IP:Port  hoặc  TCP:IP:Port", fontSize = 11.sp) },
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
                    contentColor   = if (isSelected) Color.White else Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("${zone.emoji}${zone.label}", fontSize = 11.sp)
            }
        }
    }
}
