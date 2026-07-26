package com.example.stonephopro.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.utils.clover.CloverConfig
import com.example.stonephopro.utils.clover.CloverOrder
import com.example.stonephopro.utils.clover.CloverRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val COLOR_OCCUPIED = Color(0xFF1565C0)   // xanh đậm = có order
private val COLOR_EMPTY    = Color(0xFFD0D0D0)   // xám = trống
private val COLOR_SELECTED = Color(0xFF0D47A1)   // xanh đậm hơn = đang chọn

// ── Layout bàn theo sơ đồ thực tế nhà hàng ────────────────────────────────────
//
//  Col:    0     1     2     3     4     5
//  Row 0:  .     .     .     .     .     .      ← hàng trống (lối vào/khoảng trống)
//  Row 1:  1     2     3     4     .   Outside  ← bàn 1-4 + Outside cùng hàng
//  Row 2:  .     5     6     7     .     .      ← bàn 5-7, lùi 1 ô
//  Row 3:  8     9    10    11     .     .      ← bàn 8-11
//  Row 4: 12    13    14    15     .     .      ← bàn 12-15
//  Row 5: 16    17    18    .      .     .      ← bàn 16-18
//
private data class TableSlot(val row: Int, val col: Int, val name: String, val seats: Int = 4)

private val DINING_ROOM_LAYOUT = listOf(
    // Row 1: Bàn 1-4 + Outside
    TableSlot(1, 0, "1"),  TableSlot(1, 1, "2"),  TableSlot(1, 2, "3"),  TableSlot(1, 3, "4"),
    TableSlot(1, 5, "OUTS"),
    // Row 2: Bàn 5-7 (lùi 1 ô)
    TableSlot(2, 1, "5"),  TableSlot(2, 2, "6"),  TableSlot(2, 3, "7"),
    // Row 3: Bàn 8-11
    TableSlot(3, 0, "8"),  TableSlot(3, 1, "9"),  TableSlot(3, 2, "10"), TableSlot(3, 3, "11"),
    // Row 4: Bàn 12-15
    TableSlot(4, 0, "12"), TableSlot(4, 1, "13"), TableSlot(4, 2, "14"), TableSlot(4, 3, "15"),
    // Row 5: Bàn 16-18
    TableSlot(5, 0, "16"), TableSlot(5, 1, "17"), TableSlot(5, 2, "18"),
)
private const val GRID_ROWS = 6
private const val GRID_COLS = 6

@Composable
fun CloverOrderScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var openOrders    by remember { mutableStateOf<List<CloverOrder>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf("") }
    var selectedOrder by remember { mutableStateOf<CloverOrder?>(null) }
    var selectedTab   by remember { mutableStateOf(0) }

    val tableOrderMap by remember(openOrders) {
        derivedStateOf {
            openOrders
                .filter  { it.title.isNotBlank() }
                .associateBy { it.title.trim() }
        }
    }
    val toGoOrders by remember(openOrders) {
        derivedStateOf {
            openOrders.filter { order ->
                val title = order.title.trim()
                val type  = order.orderType?.label?.lowercase() ?: ""
                title.isEmpty() || type.contains("go") || type.contains("takeout") || type.contains("pickup")
            }
        }
    }

    fun reload() {
        isLoading = true
        errorMsg  = ""
        scope.launch {
            // Gọi backend proxy — không cần token Clover, backend tự authenticate
            CloverRepository.fetchOpenOrdersViaProxy(CloverConfig.PROXY_SECRET)
                .onSuccess { orders ->
                    // Chỉ giữ orders chưa thanh toán: open = đang mở, locked = đang phục vụ
                    openOrders = orders.filter { it.state == "open" || it.state == "locked" }
                }
                .onFailure { errorMsg = "Lỗi backend: ${it.message}" }
            isLoading = false
        }
    }

    // Load ngay khi mở + auto-refresh mỗi 10 giây
    LaunchedEffect(Unit) {
        reload()
        while (true) {
            delay(10_000)
            if (!isLoading) reload()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A237E))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🖥️", fontSize = 20.sp)
                Text("Clover Dining", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Box(
                    modifier = Modifier
                        .background(
                            if (errorMsg.isEmpty()) Color(0xFF43A047) else Color(0xFFEF6C00),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (errorMsg.isEmpty()) "● Live · ↻ 10s" else "● Lỗi kết nối",
                        color = Color.White, fontSize = 11.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // Lỗi backend
        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg,
                color = Color(0xFFD32F2F),
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFEBEE))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Thông báo trạng thái
        if (!isLoading) {
            val occupiedCount = tableOrderMap.size
            val statusText = if (openOrders.isEmpty())
                "Không có order nào đang mở"
            else
                "${openOrders.size} order đang mở · $occupiedCount bàn có khách"
            Text(
                text = statusText,
                fontSize = 11.sp,
                color = Color(0xFF616161),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ── Body ─────────────────────────────────────────────────────────────
        if (isLoading && openOrders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Đang tải dữ liệu từ server...")
                }
            }
        } else {
            // Tabs
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
                        val badge = if (idx == 1 && toGoOrders.isNotEmpty()) " (${toGoOrders.size})" else ""
                        Text(
                            label + badge,
                            color = if (active) Color.White else Color(0xFF3949AB),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                val mapWeight = if (selectedOrder != null) 0.55f else 1f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(mapWeight)
                        .padding(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> DiningFloorPlan(
                            tableOrderMap = tableOrderMap,
                            selectedOrder = selectedOrder,
                            onTableClick  = { order ->
                                selectedOrder = if (selectedOrder?.id == order?.id) null else order
                            }
                        )
                        else -> ToGoList(
                            orders = toGoOrders,
                            selectedOrder = selectedOrder,
                            onOrderClick  = { order ->
                                selectedOrder = if (selectedOrder?.id == order.id) null else order
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selectedOrder != null,
                    enter   = slideInHorizontally { it },
                    exit    = slideOutHorizontally { it }
                ) {
                    selectedOrder?.let { order ->
                        OrderDetailPanel(
                            order    = order,
                            onClose  = { selectedOrder = null },
                            modifier = Modifier.fillMaxHeight().weight(0.45f)
                        )
                    }
                }
            }
        }
    }
}

// ── Sơ đồ phòng ăn — khớp với layout Clover Dining thực tế ──────────────────
@Composable
private fun DiningFloorPlan(
    tableOrderMap: Map<String, CloverOrder>,
    selectedOrder: CloverOrder?,
    onTableClick: (CloverOrder?) -> Unit
) {
    val slotMap = remember { DINING_ROOM_LAYOUT.associateBy { Pair(it.row, it.col) } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gap      = 8.dp
        val cellSize = minOf(
            (maxWidth  - gap * (GRID_COLS - 1)) / GRID_COLS,
            (maxHeight - gap * (GRID_ROWS - 1)) / GRID_ROWS
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            for (row in 0 until GRID_ROWS) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    for (col in 0 until GRID_COLS) {
                        val slot  = slotMap[Pair(row, col)]
                        val order = slot?.let { tableOrderMap[it.name] }
                        if (slot != null) {
                            TableCell(
                                label      = slot.name,
                                seats      = slot.seats,
                                isOccupied = order != null,
                                isSelected = selectedOrder?.id == order?.id,
                                total      = order?.total,
                                onClick    = { onTableClick(order) },
                                modifier   = Modifier.size(cellSize)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(cellSize))
                        }
                    }
                }
            }
        }
    }
}

// ── Table cell ────────────────────────────────────────────────────────────────
@Composable
private fun TableCell(
    label: String,
    seats: Int,
    isOccupied: Boolean,
    isSelected: Boolean,
    total: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> COLOR_SELECTED
        isOccupied -> COLOR_OCCUPIED
        else       -> COLOR_EMPTY
    }
    val textColor = if (isOccupied || isSelected) Color.White else Color(0xFF1A237E)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (isSelected) Modifier.border(2.dp, Color(0xFF64B5F6), RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🪑", fontSize = 12.sp)
            Text(
                label,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (label.length <= 2) 18.sp else 12.sp
            )
            Text(
                if (isOccupied && total != null) formatCents(total) else "$seats 🪑",
                color = textColor.copy(alpha = 0.75f),
                fontSize = 10.sp
            )
        }
    }
}

// ── To Go list ────────────────────────────────────────────────────────────────
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
                verticalAlignment     = Alignment.CenterVertically
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

// ── Order detail panel ────────────────────────────────────────────────────────
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

    Surface(modifier = modifier, shadowElevation = 8.dp, color = Color(0xFFFAFAFA)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1565C0))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (order.title.isNotBlank())
                            "Bàn ${order.title}"
                        else "Order #${order.id.takeLast(6)}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    if (timeStr.isNotEmpty())
                        Text(timeStr, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                TextButton(onClick = onClose) {
                    Text("✕", color = Color.White, fontSize = 18.sp)
                }
            }

            // Items
            LazyColumn(
                modifier             = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding       = PaddingValues(vertical = 8.dp),
                verticalArrangement  = Arrangement.spacedBy(4.dp)
            ) {
                if (items.isEmpty()) {
                    item { Text("(Không có món)", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                } else {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Text("Tên món",  modifier = Modifier.weight(1f),   fontSize = 12.sp, color = Color.Gray)
                            Text("SL",       modifier = Modifier.width(34.dp), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            Text("Đơn giá", modifier = Modifier.width(72.dp), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
                            Text("T.Tiền",  modifier = Modifier.width(72.dp), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
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
                                if (lineItem.quantity % 1.0 == 0.0) lineItem.quantity.toInt().toString()
                                else "%.1f".format(lineItem.quantity),
                                modifier = Modifier.width(34.dp), fontSize = 14.sp, textAlign = TextAlign.Center
                            )
                            Text(
                                formatCents(lineItem.price),
                                modifier = Modifier.width(72.dp), fontSize = 14.sp, textAlign = TextAlign.End
                            )
                            Text(
                                formatCents(lineItem.lineTotal),
                                modifier = Modifier.width(72.dp), fontSize = 14.sp,
                                textAlign = TextAlign.End, fontWeight = FontWeight.Medium
                            )
                        }
                        Divider(color = Color(0xFFEEEEEE))
                    }
                }
            }

            // Total
            Divider(thickness = 1.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("TỔNG CỘNG", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    formatCents(order.total),
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1565C0)
                )
            }
        }
    }
}

private fun formatCents(cents: Long): String {
    val dollars   = cents / 100
    val remainder = cents % 100
    return "$%d.%02d".format(dollars, remainder)
}
