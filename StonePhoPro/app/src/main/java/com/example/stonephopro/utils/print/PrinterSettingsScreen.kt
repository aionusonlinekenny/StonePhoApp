// PrinterSettingsScreen.kt
package com.example.stonephopro.utils.print

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.components.Button3D
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicInteger
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import com.example.stonephopro.utils.SocketPrinter
import com.example.stonephopro.utils.PrinterConfig

private fun isWifiConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun PrinterSettingsScreen(onBack: () -> Unit) {
    val printers = remember { mutableStateListOf<String>() }
    var isSearching by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanJob: Job? by remember { mutableStateOf(null) }

    val wifiConnected by remember { derivedStateOf { isWifiConnected(context) } }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("⚙️ Cài đặt máy in", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        // Cảnh báo WiFi chưa kết nối
        if (!wifiConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFE082), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠️ Chưa kết nối WiFi! Vui lòng kết nối WiFi cùng mạng với máy in trước khi tìm kiếm.",
                    fontSize = 13.sp,
                    color = Color(0xFF5D4037)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Button3D với hiệu ứng gradient
        Button3D(
            text = if (isSearching) "🔍 Đang tìm..." else "🔍 Tìm máy in trong mạng",
            onClick = {
                if (isSearching) return@Button3D

                if (!isWifiConnected(context)) {
                    status = "⚠️ Chưa kết nối WiFi! Hãy bật WiFi và kết nối vào cùng mạng với máy in."
                    return@Button3D
                }

                isSearching = true
                status = "🔄 Đang quét IP..."
                progress = 0f
                printers.clear()

                scanJob = scope.launch(Dispatchers.IO) {
                    scanLANForPrinters(
                        onPrinterFound = { printer ->
                            printers.add(printer)
                        },
                        onProgressUpdate = { progressValue, currentIP ->
                            progress = progressValue
                            status = "🔄 Đang quét: $currentIP (${printers.size} máy in)"
                        },
                        timeout = 500
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

// Quét song song nhiều IP cùng lúc, giới hạn đồng thời bằng Semaphore
suspend fun scanLANForPrinters(
    onPrinterFound: (String) -> Unit,
    onProgressUpdate: (Float, String) -> Unit,
    timeout: Int = 500,
    concurrency: Int = 50
) {
    val baseIp = getLocalSubnet()
    val totalScans = 254
    val completedScans = AtomicInteger(0)
    val semaphore = Semaphore(concurrency)

    coroutineScope {
        for (i in 1..254) {
            val ip = "$baseIp.$i"
            launch(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    withContext(Dispatchers.Main) {
                        onProgressUpdate(completedScans.get() / totalScans.toFloat(), ip)
                    }
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, 9100), timeout)
                            withContext(Dispatchers.Main) {
                                onPrinterFound("TCP:$ip:9100")
                            }
                        }
                    } catch (_: Exception) {}
                    completedScans.incrementAndGet()
                } finally {
                    semaphore.release()
                }
            }
        }
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
