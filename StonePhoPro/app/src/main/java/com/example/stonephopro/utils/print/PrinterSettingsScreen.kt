// PrinterSettingsScreen.kt
package com.example.stonephopro.utils.print

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.components.Button3D
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import com.example.stonephopro.utils.SocketPrinter
import com.example.stonephopro.utils.PrinterConfig
@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun PrinterSettingsScreen(onBack: () -> Unit) {
    val printers = remember { mutableStateListOf<String>() }
    var isSearching by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanJob: Job? by remember { mutableStateOf(null) } // ✅ Job để quản lý coroutine

    Column(modifier = Modifier.padding(16.dp)) {
        Text("⚙️ Cài đặt máy in", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // ✅ Button3D với hiệu ứng gradient
        Button3D(
            text = if (isSearching) "🔍 Đang tìm..." else "🔍 Tìm máy in trong mạng",
            onClick = {
                if (isSearching) return@Button3D

                isSearching = true
                status = "🔄 Đang quét IP..."
                progress = 0f
                printers.clear() // Đặt lại danh sách máy in

                scanJob = scope.launch(Dispatchers.IO) {
                    scanLANForPrinters(
                        onPrinterFound = { printer ->
                            printers.add(printer) // ✅ Thêm vào danh sách ngay khi tìm thấy
                        },
                        onProgressUpdate = { progressValue, currentIP ->
                            progress = progressValue
                            status = "🔄 Đang quét: $currentIP (${printers.size} máy in)"
                        },
                        delayTime = 10L,
                        timeout = 100
                    )
                    withContext(Dispatchers.Main) {
                        isSearching = false
                        status = "✅ Đã hoàn thành quét. Tìm thấy ${printers.size} máy in"
                    }
                }
            },
            modifier = Modifier.height(50.dp),
            fontSize = 16.sp,
            gradientColors = if (isSearching)
                listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
            else
                listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Đang quét: ${"%.0f".format(progress * 100)}%", fontSize = 14.sp)
        }

        LazyColumn {
            items(printers) { printer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            PrinterConfig.saveSelectedPrinter(context, printer)
                            val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)

                            scope.launch {
                                val testText = "\n\n=== TEST PRINT ===\nPrint Test Success\n==================\n\n"
                                SocketPrinter.printText(ip, port, testText)
                                status = "✅ Đã chọn và in thử: $ip:$port"
                            }
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(printer, fontSize = 15.sp)
                    Text("🖨️")
                }
            }
        }

        if (status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(status)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button3D(
            text = "⬅ Quay lại",
            onClick = {
                scanJob?.cancel() // ✅ Dừng tiến trình quét nếu đang chạy
                isSearching = false
                status = "❌ Đã dừng quét."
                onBack()
            },
            modifier = Modifier.height(50.dp),
            fontSize = 16.sp,
            gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
        )
    }
}

// ✅ Hàm scanLANForPrinters với danh sách cập nhật nhanh
suspend fun scanLANForPrinters(
    onPrinterFound: (String) -> Unit,
    onProgressUpdate: (Float, String) -> Unit,
    delayTime: Long = 10L,
    timeout: Int = 500
) {
    val baseIp = getLocalSubnet()
    val totalScans = 254
    var completedScans = 0

    for (i in 1..254) {
        val ip = "$baseIp.$i"
        withContext(Dispatchers.Main) {
            onProgressUpdate(completedScans / totalScans.toFloat(), ip)
        }
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 9100), timeout)
                val printer = "TCP:$ip:9100"
                withContext(Dispatchers.Main) {
                    onPrinterFound(printer)
                }
            }
        } catch (_: Exception) {}

        delay(delayTime)
        completedScans++
    }
}

// ✅ Hàm lấy subnet — ưu tiên interface WiFi (wlan) để tránh lấy nhầm mạng di động
private fun getLocalSubnet(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "192.168.0"
        val allIfaces = interfaces.toList()

        // Ưu tiên interface tên chứa "wlan" (WiFi trên Android)
        for (iface in allIfaces) {
            if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) continue
            if (!iface.name.contains("wlan", ignoreCase = true)) continue
            for (addr in iface.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress ?: continue
                    return host.substringBeforeLast(".")
                }
            }
        }

        // Fallback: bất kỳ interface IPv4 nào không phải loopback / point-to-point
        for (iface in allIfaces) {
            if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) continue
            for (addr in iface.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress ?: continue
                    return host.substringBeforeLast(".")
                }
            }
        }
    } catch (_: Exception) {}
    return "192.168.0" // fallback
}
