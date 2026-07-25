package com.example.stonephopro.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.utils.clover.CloverConfig
import com.example.stonephopro.utils.clover.CloverOrder
import com.example.stonephopro.utils.clover.CloverRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CloverOrderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (savedUrl, savedMid, savedToken) = remember { CloverConfig.load(context) }
    var baseUrl by remember { mutableStateOf(savedUrl) }
    var merchantId by remember { mutableStateOf(savedMid) }
    var accessToken by remember { mutableStateOf(savedToken) }

    var showConfig by remember { mutableStateOf(savedMid.isEmpty() || savedToken.isEmpty()) }
    var showToken by remember { mutableStateOf(false) }

    var orders by remember { mutableStateOf<List<CloverOrder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var expandedOrderId by remember { mutableStateOf<String?>(null) }

    fun loadOrders() {
        if (merchantId.isBlank() || accessToken.isBlank()) {
            errorMsg = "Vui lòng nhập Merchant ID và Access Token."
            showConfig = true
            return
        }
        CloverConfig.save(context, baseUrl, merchantId, accessToken)
        isLoading = true
        errorMsg = ""
        orders = emptyList()
        scope.launch {
            CloverRepository.fetchOpenOrders(baseUrl, merchantId, accessToken)
                .onSuccess { orders = it; if (it.isEmpty()) errorMsg = "Không có order nào đang mở." }
                .onFailure { errorMsg = "Lỗi: ${it.message}" }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🖥️ Clover POS Orders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button3D(
                text = "⬅ Quay lại",
                onClick = onBack,
                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)),
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Config section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showConfig = !showConfig }
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("⚙️ Cấu hình kết nối", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(if (showConfig) "▲ Thu gọn" else "▼ Mở rộng", fontSize = 13.sp, color = Color.Gray)
        }

        AnimatedVisibility(visible = showConfig) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (IP thiết bị hoặc api.clover.com)") },
                    placeholder = { Text("https://192.168.1.x  hoặc  https://api.clover.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = merchantId,
                    onValueChange = { merchantId = it },
                    label = { Text("Merchant ID (mId)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = { Text("Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (showToken) "Ẩn" else "Hiện",
                            modifier = Modifier.clickable { showToken = !showToken }.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Load button
        Button3D(
            text = if (isLoading) "⏳ Đang tải..." else "🔄 Lấy order đang mở",
            onClick = { if (!isLoading) loadOrders() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            gradientColors = if (isLoading)
                listOf(Color(0xFFBDBDBD), Color(0xFF9E9E9E))
            else
                listOf(Color(0xFF66BB6A), Color(0xFF2E7D32))
        )

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg, color = Color(0xFFD32F2F), fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Orders list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    isExpanded = expandedOrderId == order.id,
                    onClick = {
                        expandedOrderId = if (expandedOrderId == order.id) null else order.id
                    }
                )
            }
        }
    }
}

@Composable
private fun OrderCard(order: CloverOrder, isExpanded: Boolean, onClick: () -> Unit) {
    val timeStr = remember(order.createdTime) {
        if (order.createdTime > 0)
            SimpleDateFormat("HH:mm dd/MM", Locale.US).format(Date(order.createdTime))
        else ""
    }
    val totalDisplay = remember(order.total) { formatCents(order.total) }
    val items = order.lineItems?.elements ?: emptyList()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Order header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (order.title.isNotEmpty()) order.title else "Order #${order.id.takeLast(6)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (timeStr.isNotEmpty())
                        Text(timeStr, fontSize = 12.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(totalDisplay, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                    Text(if (isExpanded) "▲" else "▼", color = Color.Gray, fontSize = 13.sp)
                }
            }

            // Expanded: line items từ MAPPING TABLE (Clover inventory)
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(6.dp))

                    if (items.isEmpty()) {
                        Text("(Không có món nào)", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        // Table header
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Text("Tên món", modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text("SL", modifier = Modifier.width(40.dp), fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text("Đơn giá", modifier = Modifier.width(80.dp), fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text("Thành tiền", modifier = Modifier.width(90.dp), fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        }

                        items.forEach { lineItem ->
                            val displayName = when {
                                lineItem.item?.name?.isNotEmpty() == true -> lineItem.item.name
                                lineItem.name.isNotEmpty() -> lineItem.name
                                else -> "(Không tên)"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(displayName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                Text(
                                    text = if (lineItem.quantity == lineItem.quantity.toLong().toDouble())
                                        lineItem.quantity.toLong().toString()
                                    else "%.2f".format(lineItem.quantity),
                                    modifier = Modifier.width(40.dp),
                                    fontSize = 14.sp
                                )
                                Text(formatCents(lineItem.price), modifier = Modifier.width(80.dp), fontSize = 14.sp)
                                Text(formatCents(lineItem.lineTotal), modifier = Modifier.width(90.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(6.dp))

                        // Total row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text("Tổng cộng: ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(totalDisplay, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}

private fun formatCents(cents: Long): String {
    // Clover sử dụng đơn vị cents (USD). Hiển thị dạng $X.XX
    val dollars = cents / 100
    val centsRem = cents % 100
    return "$%d.%02d".format(dollars, centsRem)
}
