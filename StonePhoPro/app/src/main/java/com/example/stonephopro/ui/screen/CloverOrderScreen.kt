package com.example.stonephopro.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.utils.clover.CloverAuthManager
import com.example.stonephopro.utils.clover.CloverConfig
import com.example.stonephopro.utils.clover.CloverOrder
import com.example.stonephopro.utils.clover.CloverRepository
import com.example.stonephopro.utils.clover.CloverTable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val COLOR_OCCUPIED   = Color(0xFF1565C0)   // xanh đậm — có khách
private val COLOR_EMPTY      = Color(0xFFEEEEEE)   // xám nhạt — trống
private val COLOR_TOGO       = Color(0xFF6A1B9A)   // tím — To Go

@Composable
fun CloverOrderScreen(onBack: () -> Unit) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()

    // Kiểm tra xem đã OAuth chưa
    var isAuthenticated by remember { mutableStateOf(CloverAuthManager.isAuthenticated(context)) }

    val (savedUrl, savedMid, savedToken) = remember { CloverConfig.load(context) }
    var baseUrl     by remember { mutableStateOf(savedUrl) }
    var merchantId  by remember { mutableStateOf(savedMid) }
    var accessToken by remember { mutableStateOf(savedToken) }
    // Chỉ mở config thủ công nếu chưa OAuth và cũng chưa nhập tay
    var showConfig  by remember { mutableStateOf(!isAuthenticated && (savedMid.isEmpty() || savedToken.isEmpty())) }
    var showToken   by remember { mutableStateOf(false) }

    var tables      by remember { mutableStateOf<List<CloverTable>>(emptyList()) }
    var openOrders  by remember { mutableStateOf<List<CloverOrder>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }

    // Bàn/order đang được chọn để xem chi tiết
    var selectedOrder by remember { mutableStateOf<CloverOrder?>(null) }

    // Tab: "Main Dining" vs "To Go"
    var selectedTab by remember { mutableStateOf(0) }   // 0 = Dine In, 1 = To Go

    // Map: tableLabel -> CloverOrder (chỉ lấy order đầu tiên nếu có nhiều)
    val tableOrderMap: Map<String, CloverOrder> by remember(openOrders) {
        derivedStateOf { openOrders.associateBy { it.tableLabel ?: "" } }
    }

    // Danh sách To Go orders (không có tableLabel hoặc orderType.label chứa "go"/"to go")
    val toGoOrders by remember(openOrders) {
        derivedStateOf {
            openOrders.filter { order ->
                val label = order.tableLabel?.trim() ?: ""
                val typeLabel = order.orderType?.label?.lowercase() ?: ""
                label.isEmpty() || typeLabel.contains("go") || typeLabel.contains("takeout")
            }
        }
    }

    fun reload() {
        // Ưu tiên token OAuth nếu đã kết nối
        if (CloverAuthManager.isAuthenticated(context)) {
            isAuthenticated = true
            val oauthMid   = CloverAuthManager.getMerchantId(context)
            val oauthToken = CloverAuthManager.getToken(context)
            if (oauthMid.isNotBlank())   merchantId  = oauthMid
            if (oauthToken.isNotBlank()) accessToken = oauthToken
            baseUrl = "https://api.clover.com"
        }
        if (merchantId.isBlank() || accessToken.isBlank()) {
            errorMsg = "Vui lòng kết nối Clover hoặc nhập thủ công Merchant ID và Token."
            showConfig = true
            return
        }
        CloverConfig.save(context, baseUrl, merchantId, accessToken)
        isLoading = true
        errorMsg  = ""
        selectedOrder = null
        scope.launch {
            // Lấy song song: bảng bàn + open orders
            val ordersResult = CloverRepository.fetchOpenOrders(baseUrl, merchantId, accessToken)
            val tablesResult = CloverRepository.fetchTables(baseUrl, merchantId, accessToken)

            ordersResult
                .onSuccess  { openOrders = it }
                .onFailure  { errorMsg = "Lỗi order: ${it.message}" }

            tablesResult
                .onSuccess  { if (it.isNotEmpty()) tables = it.sortedBy { t -> t.name.padStart(5, '0') } }
                // Nếu không có table service → tự build từ tableLabel của orders
                .onFailure  {
                    val fromOrders = openOrders
                        .mapNotNull { o -> o.tableLabel?.takeIf { l -> l.isNotBlank() } }
                        .distinct()
                        .sorted()
                        .map { lbl -> CloverTable(id = lbl, name = lbl) }
                    if (fromOrders.isNotEmpty()) tables = fromOrders
                }

            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A237E))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🖥️", fontSize = 20.sp)
                    Text("Clover Dining", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    // Badge trạng thái kết nối
                    Box(
                        modifier = Modifier
                            .background(
                                if (isAuthenticated) Color(0xFF43A047) else Color(0xFFEF6C00),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isAuthenticated) "● Đã kết nối" else "● Chưa kết nối",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Nút OAuth — hiển thị khi chưa kết nối
                    if (!isAuthenticated) {
                        Button3D(
                            text = "🔐 Kết nối Clover",
                            onClick = {
                                val authUrl = CloverAuthManager.buildAuthUrl()
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                )
                            },
                            gradientColors = listOf(Color(0xFFFF8F00), Color(0xFFE65100)),
                            fontSize = 13.sp
                        )
                    } else {
                        TextButton(onClick = {
                            CloverAuthManager.logout(context)
                            isAuthenticated = false
                            merchantId = ""
                            accessToken = ""
                        }) {
                            Text("Đăng xuất", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = { showConfig = !showConfig }) {
                        Text("⚙️", fontSize = 18.sp)
                    }
                    IconButton(onClick = { if (!isLoading) reload() }) {
                        Text(if (isLoading) "⏳" else "🔄", fontSize = 18.sp)
                    }
                    Button3D(
                        text = "✕ Đóng",
                        onClick = onBack,
                        gradientColors = listOf(Color(0xFF546E7A), Color(0xFF263238)),
                        fontSize = 13.sp
                    )
                }
            }

            // ── Config panel ─────────────────────────────────────────────────
            AnimatedVisibility(visible = showConfig) {
                Surface(shadowElevation = 4.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://192.168.x.x  hoặc  https://api.clover.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        OutlinedTextField(
                            value = merchantId,
                            onValueChange = { merchantId = it },
                            label = { Text("Merchant ID (mId)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
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
                        Button3D(
                            text = "💾 Lưu & Kết nối",
                            onClick = { showConfig = false; reload() },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            gradientColors = listOf(Color(0xFF42A5F5), Color(0xFF1565C0))
                        )
                    }
                }
            }

            if (errorMsg.isNotEmpty()) {
                Text(
                    text = errorMsg,
                    color = Color(0xFFD32F2F),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFFFEBEE)).padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // ── Tabs: Main Dining / To Go ─────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Main Dining Room", "TO GO").forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (active) Color(0xFF1A237E) else Color(0xFFE8EAF6))
                            .clickable { selectedTab = idx; selectedOrder = null }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (active) Color.White else Color(0xFF3949AB),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Body ─────────────────────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Đang tải dữ liệu từ Clover...")
                    }
                }
            } else if (tables.isEmpty() && openOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔌", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chưa có dữ liệu", fontSize = 16.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button3D(
                            text = "🔄 Kết nối Clover",
                            onClick = { if (merchantId.isBlank()) showConfig = true else reload() },
                            gradientColors = listOf(Color(0xFF42A5F5), Color(0xFF1565C0))
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // ── Table map ─────────────────────────────────────────
                    val mapWeight = if (selectedOrder != null) 0.55f else 1f
                    Box(modifier = Modifier.fillMaxHeight().weight(mapWeight).padding(12.dp)) {
                        when (selectedTab) {
                            0 -> DiningRoomGrid(
                                tables = tables.filter { t ->
                                    val lbl = t.name.lowercase()
                                    !lbl.contains("go") && !lbl.contains("takeout")
                                },
                                tableOrderMap = tableOrderMap,
                                selectedOrder = selectedOrder,
                                onTableClick = { order -> selectedOrder = if (selectedOrder?.id == order?.id) null else order }
                            )
                            else -> ToGoList(
                                orders = toGoOrders,
                                selectedOrder = selectedOrder,
                                onOrderClick = { order -> selectedOrder = if (selectedOrder?.id == order.id) null else order }
                            )
                        }
                    }

                    // ── Order detail panel ────────────────────────────────
                    AnimatedVisibility(
                        visible = selectedOrder != null,
                        enter = slideInHorizontally { it },
                        exit = slideOutHorizontally { it }
                    ) {
                        selectedOrder?.let { order ->
                            OrderDetailPanel(
                                order = order,
                                onClose = { selectedOrder = null },
                                modifier = Modifier.fillMaxHeight().weight(0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Table grid cho Main Dining Room ──────────────────────────────────────────
@Composable
private fun DiningRoomGrid(
    tables: List<CloverTable>,
    tableOrderMap: Map<String, CloverOrder>,
    selectedOrder: CloverOrder?,
    onTableClick: (CloverOrder?) -> Unit
) {
    if (tables.isEmpty()) {
        // Không có bàn từ API → hiển thị từ open orders
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không tìm thấy bàn.\nVui lòng kiểm tra lại kết nối.", textAlign = TextAlign.Center, color = Color.Gray)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tables, key = { it.id }) { table ->
            val order = tableOrderMap[table.name]
            val isOccupied = order != null
            val isSelected = selectedOrder?.id == order?.id
            TableCell(
                label = table.name,
                seats = table.maxSeats,
                isOccupied = isOccupied,
                isSelected = isSelected,
                total = order?.total,
                onClick = { onTableClick(order) }
            )
        }
    }
}

// ── Một ô bàn ─────────────────────────────────────────────────────────────────
@Composable
private fun TableCell(
    label: String,
    seats: Int,
    isOccupied: Boolean,
    isSelected: Boolean,
    total: Long?,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected  -> Color(0xFF0D47A1)
        isOccupied  -> COLOR_OCCUPIED
        else        -> COLOR_EMPTY
    }
    val textColor = if (isOccupied || isSelected) Color.White else Color(0xFF424242)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (isSelected) Modifier.border(2.dp, Color(0xFF64B5F6), RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(enabled = isOccupied, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (seats > 0 && !isOccupied) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪑", fontSize = 10.sp)
                    Text(" $seats", fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
                }
            }
            if (isOccupied && total != null) {
                Text(formatCents(total), color = textColor.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Danh sách To Go orders ────────────────────────────────────────────────────
@Composable
private fun ToGoList(
    orders: List<CloverOrder>,
    selectedOrder: CloverOrder?,
    onOrderClick: (CloverOrder) -> Unit
) {
    if (orders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không có order To Go đang mở", color = Color.Gray)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(orders, key = { it.id }) { order ->
            val isSelected = selectedOrder?.id == order.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) COLOR_OCCUPIED else Color(0xFFEDE7F6))
                    .clickable { onOrderClick(order) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.title.ifEmpty { "Order #${order.id.takeLast(6)}" },
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFF4A148C)
                    )
                    Text(
                        text = order.orderType?.label ?: "To Go",
                        fontSize = 12.sp,
                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Gray
                    )
                }
                Text(
                    formatCents(order.total),
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color(0xFF4A148C)
                )
            }
        }
    }
}

// ── Panel chi tiết order bên phải ────────────────────────────────────────────
@Composable
private fun OrderDetailPanel(
    order: CloverOrder,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeStr = remember(order.createdTime) {
        if (order.createdTime > 0)
            SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.US).format(Date(order.createdTime))
        else ""
    }
    val items = order.lineItems?.elements ?: emptyList()

    Surface(
        modifier = modifier,
        shadowElevation = 8.dp,
        color = Color(0xFFFAFAFA)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Panel header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1565C0))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (order.tableLabel?.isNotBlank() == true)
                            "Bàn ${order.tableLabel}"
                        else order.title.ifEmpty { "Order #${order.id.takeLast(6)}" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (timeStr.isNotEmpty())
                        Text(timeStr, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                TextButton(onClick = onClose) {
                    Text("✕", color = Color.White, fontSize = 18.sp)
                }
            }

            // Items list
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (items.isEmpty()) {
                    item { Text("(Không có món)", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                } else {
                    // Column header
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Text("Tên món",    modifier = Modifier.weight(1f),   fontSize = 12.sp, color = Color.Gray)
                            Text("SL",         modifier = Modifier.width(34.dp), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            Text("Đơn giá",   modifier = Modifier.width(72.dp), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
                            Text("T.Tiền",    modifier = Modifier.width(72.dp), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
                        }
                        Divider()
                    }
                    items(items, key = { it.id }) { lineItem ->
                        val displayName = lineItem.item?.name?.takeIf { it.isNotBlank() }
                            ?: lineItem.name.takeIf { it.isNotBlank() }
                            ?: "(Không tên)"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(displayName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Text(
                                text = if (lineItem.quantity % 1.0 == 0.0) lineItem.quantity.toInt().toString()
                                       else "%.1f".format(lineItem.quantity),
                                modifier = Modifier.width(34.dp),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(formatCents(lineItem.price),    modifier = Modifier.width(72.dp), fontSize = 14.sp, textAlign = TextAlign.End)
                            Text(formatCents(lineItem.lineTotal), modifier = Modifier.width(72.dp), fontSize = 14.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Medium)
                        }
                        Divider(color = Color(0xFFEEEEEE))
                    }
                }
            }

            // Total footer
            Divider(thickness = 1.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TỔNG CỘNG", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    formatCents(order.total),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1565C0)
                )
            }
        }
    }
}

private fun formatCents(cents: Long): String {
    val dollars  = cents / 100
    val remainder = cents % 100
    return "$%d.%02d".format(dollars, remainder)
}
