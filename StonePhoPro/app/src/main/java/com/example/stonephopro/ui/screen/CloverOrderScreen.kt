package com.example.stonephopro.ui.screen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.model.Invoice
import com.example.stonephopro.model.Product
import com.example.stonephopro.utils.InvoiceStorage
import com.example.stonephopro.utils.PrinterConfig
import com.example.stonephopro.utils.SocketPrinter
import com.example.stonephopro.utils.clover.CloverConfig
import com.example.stonephopro.utils.clover.CloverLineItem
import com.example.stonephopro.utils.clover.CloverOrder
import com.example.stonephopro.utils.clover.CloverRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val COLOR_OCCUPIED = Color(0xFF1565C0)
private val COLOR_EMPTY    = Color.White
private val COLOR_SELECTED = Color(0xFF0D47A1)

private data class TableSlot(
    val row: Int,
    val col: Int,
    val name: String,                        // display label trên cell
    val seats: Int = 4,
    val cloverTitle: String = name           // khớp với order.title từ Clover
)

private val DINING_ROOM_LAYOUT = listOf(
    TableSlot(1, 1, "1"),  TableSlot(1, 2, "2"),  TableSlot(1, 3, "3"),  TableSlot(1, 4, "4"),
    TableSlot(1, 5, "OUTS", cloverTitle = "OUTSIDE"),
    TableSlot(2, 2, "5"),  TableSlot(2, 3, "6"),  TableSlot(2, 4, "7"),
    TableSlot(3, 1, "8"),  TableSlot(3, 2, "9"),  TableSlot(3, 3, "10"), TableSlot(3, 4, "11"),
    TableSlot(4, 1, "12"), TableSlot(4, 2, "13"), TableSlot(4, 3, "14"), TableSlot(4, 4, "15"),
    TableSlot(5, 1, "16"), TableSlot(5, 2, "17"), TableSlot(5, 3, "18"),
)
private const val GRID_ROWS = 6
private const val GRID_COLS = 6

// ── SharedPreferences helpers for local closed-table tracking ─────────────────
private const val PREFS_NAME   = "clover_local"
private const val KEY_CLOSED   = "closed_tables"
private const val SIXTEEN_HOURS = 16L * 60 * 60 * 1000

private fun loadClosedTables(ctx: Context): Map<String, Long> {
    val json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CLOSED, null) ?: return emptyMap()
    return try {
        val type = object : TypeToken<Map<String, Long>>() {}.type
        val raw: Map<String, Long>? = Gson().fromJson(json, type)
        // Purge entries older than 16 hours (yesterday's closings)
        val cutoff = System.currentTimeMillis() - SIXTEEN_HOURS
        raw?.filter { it.value > cutoff } ?: emptyMap()
    } catch (e: Exception) { emptyMap() }
}

private fun saveClosedTables(ctx: Context, tables: Map<String, Long>) {
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_CLOSED, Gson().toJson(tables)).apply()
}

// ── Main screen ───────────────────────────────────────────────────────────────
@Composable
fun CloverOrderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var openOrders    by remember { mutableStateOf<List<CloverOrder>>(emptyList()) }
    var isFirstLoad   by remember { mutableStateOf(true) }
    var isRefreshing  by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf("") }
    var selectedOrder by remember { mutableStateOf<CloverOrder?>(null) }
    var selectedTab   by remember { mutableStateOf(0) }
    var closedTables  by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // Load locally closed tables from SharedPreferences on start
    LaunchedEffect(Unit) {
        closedTables = loadClosedTables(context)
    }

    val tableOrderMap by remember(openOrders) {
        derivedStateOf {
            openOrders.filter { it.title.isNotBlank() }.associateBy { it.title.trim() }
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

    // Close a table locally (cash = save invoice first, card = just close)
    fun closeTable(tableTitle: String) {
        val now     = System.currentTimeMillis()
        val updated = closedTables + (tableTitle.trim() to now)
        closedTables = updated
        saveClosedTables(context, updated)
        openOrders    = openOrders.filter { it.title.trim() != tableTitle.trim() }
        selectedOrder = null
    }

    fun applyFilter(orders: List<CloverOrder>): List<CloverOrder> {
        // PHP pre-filters: paid orders, closed/deleted state, older than 8h.
        // Android only handles local cash closings (SharedPreferences).
        return orders
            .filter { o ->
                val closeTime = closedTables[o.title.trim()]
                closeTime == null || o.createdTime > closeTime
            }
            .groupBy { it.title.trim() }
            .mapValues { (_, list) -> list.maxByOrNull { it.createdTime }!! }
            .values.toList()
    }

    // Background refresh — updates data silently, no loading flash
    fun reload() {
        isRefreshing = true
        scope.launch {
            CloverRepository.fetchOpenOrdersViaProxy(CloverConfig.PROXY_SECRET)
                .onSuccess { orders ->
                    openOrders = applyFilter(orders)
                    errorMsg   = ""
                }
                .onFailure { err ->
                    if (isFirstLoad) errorMsg = "Lỗi backend: ${err.message}"
                }
            isFirstLoad  = false
            isRefreshing = false
        }
    }

    // Initial load + silent 5s auto-refresh
    LaunchedEffect(Unit) {
        reload()
        while (true) {
            delay(5_000)
            reload()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {

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
                        if (errorMsg.isEmpty()) "● Live · ↻ 5s" else "● Lỗi kết nối",
                        color = Color.White, fontSize = 11.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { reload() }) {
                    Text(if (isRefreshing) "⏳" else "🔄", fontSize = 18.sp)
                }
                Button3D(
                    text = "✕ Đóng",
                    onClick = onBack,
                    gradientColors = listOf(Color(0xFF546E7A), Color(0xFF263238)),
                    fontSize = 13.sp
                )
            }
        }

        // Error banner
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

        // Status bar — always visible, never flickers
        val occupiedCount = tableOrderMap.size
        Text(
            text = if (isFirstLoad) "Đang tải..."
                   else if (openOrders.isEmpty()) "Không có order nào đang mở"
                   else "${openOrders.size} orders · $occupiedCount bàn có khách",
            fontSize = 11.sp,
            color = Color(0xFF616161),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ── Body ─────────────────────────────────────────────────────────────
        if (isFirstLoad) {
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

            Row(modifier = Modifier.fillMaxSize().background(Color.White)) {
                val mapWeight = if (selectedOrder != null) 0.55f else 1f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(mapWeight)
                        .background(Color.White)
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
                            order          = order,
                            onClose        = { selectedOrder = null },
                            onCloseTable   = { closeTable(order.title) },
                            modifier       = Modifier.fillMaxHeight().weight(0.45f)
                        )
                    }
                }
            }
        }
    }
}

// ── Floor plan ────────────────────────────────────────────────────────────────
@Composable
private fun DiningFloorPlan(
    tableOrderMap: Map<String, CloverOrder>,
    selectedOrder: CloverOrder?,
    onTableClick: (CloverOrder?) -> Unit
) {
    val slotMap = remember { DINING_ROOM_LAYOUT.associateBy { Pair(it.row, it.col) } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White).padding(8.dp)) {
        val totalGapH = maxWidth  * 0.15f
        val totalGapV = maxHeight * 0.15f
        val cellSize  = minOf(
            (maxWidth  - totalGapH) / GRID_COLS,
            (maxHeight - totalGapV) / GRID_ROWS
        )
        Column(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            for (row in 0 until GRID_ROWS) {
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (col in 0 until GRID_COLS) {
                        val slot  = slotMap[Pair(row, col)]
                        val order = slot?.let { tableOrderMap[it.cloverTitle] }
                        if (slot != null) {
                            TableCell(
                                label      = slot.name,
                                seats      = slot.seats,
                                isOccupied = order != null,
                                isSelected = order != null && selectedOrder?.id == order.id,
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
    val bgColor   = when {
        isSelected -> COLOR_SELECTED
        isOccupied -> COLOR_OCCUPIED
        else       -> Color.White
    }
    val textColor = if (isOccupied || isSelected) Color.White else Color(0xFF1565C0)
    val shape     = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .background(bgColor, shape)
            .then(
                when {
                    isSelected -> Modifier.border(2.5.dp, Color(0xFF64B5F6), shape)
                    !isOccupied -> Modifier.border(1.5.dp, Color(0xFF1565C0), shape)
                    else -> Modifier
                }
            )
            .clip(shape)
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
                color      = textColor,
                fontWeight = FontWeight.Bold,
                fontSize   = if (label.length <= 2) 18.sp else 12.sp
            )
            Text(
                if (isOccupied && total != null) formatCents(total) else "$seats 🪑",
                color    = textColor.copy(alpha = 0.85f),
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
    onCloseTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var payResult          by remember { mutableStateOf("") }
    var showPayDialog      by remember { mutableStateOf(false) }
    var showChangeCalc     by remember { mutableStateOf(false) }
    var receivedAmountText by remember { mutableStateOf("") }

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
                        text = if (order.title.isNotBlank()) "Bàn ${order.title}"
                               else "Order #${order.id.takeLast(6)}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    if (timeStr.isNotEmpty())
                        Text(timeStr, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                TextButton(onClick = onClose) { Text("✕", color = Color.White, fontSize = 18.sp) }
            }

            // Items list
            LazyColumn(
                modifier            = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding      = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    items(items, key = { it.id }) { li ->
                        val displayName = li.item?.name?.takeIf { it.isNotBlank() }
                            ?: li.name.takeIf { it.isNotBlank() } ?: "(Không tên)"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(displayName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Text(
                                if (li.quantity % 1.0 == 0.0) li.quantity.toInt().toString()
                                else "%.1f".format(li.quantity),
                                modifier = Modifier.width(34.dp), fontSize = 14.sp, textAlign = TextAlign.Center
                            )
                            Text(formatCents(li.price),    modifier = Modifier.width(72.dp), fontSize = 14.sp, textAlign = TextAlign.End)
                            Text(formatCents(li.lineTotal), modifier = Modifier.width(72.dp), fontSize = 14.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Medium)
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
                Text(formatCents(order.total), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1565C0))
            }

            // ── Buttons ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cash payment: open change calculator first
                Button3D(
                    text = "💵 Cash",
                    onClick = {
                        receivedAmountText = ""
                        showChangeCalc = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    fontSize = 14.sp,
                    gradientColors = listOf(Color(0xFF43A047), Color(0xFF1B5E20))
                )

            }
        }
    }

    // ── Change calculator dialog ──────────────────────────────────────────────
    if (showChangeCalc) {
        val totalDollars  = order.total / 100.0
        val received      = receivedAmountText.toDoubleOrNull() ?: 0.0
        val change        = received - totalDollars
        val canConfirm    = received >= totalDollars

        AlertDialog(
            onDismissRequest = { showChangeCalc = false },
            title = {
                Text(
                    text = if (order.title.isNotBlank()) "💵 Bàn ${order.title} — Thanh toán Cash"
                           else "💵 Thanh toán Cash",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Total due
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cần thanh toán", fontSize = 14.sp, color = Color(0xFF1565C0))
                        Text(
                            formatCents(order.total),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                    }

                    // Received amount input
                    OutlinedTextField(
                        value = receivedAmountText,
                        onValueChange = { v ->
                            // Chỉ cho phép số và dấu chấm thập phân
                            if (v.isEmpty() || v.matches(Regex("^\\d{0,6}(\\.\\d{0,2})?$"))) {
                                receivedAmountText = v
                            }
                        },
                        label = { Text("Tiền nhận từ khách ($)") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    )

                    // Change display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (canConfirm) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (canConfirm) "Tiền thối lại" else "Còn thiếu",
                            fontSize = 14.sp,
                            color = if (canConfirm) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text(
                            text = if (received == 0.0) "—"
                                   else if (canConfirm) "$%,.2f".format(change)
                                   else "-$%,.2f".format(-change),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canConfirm) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showChangeCalc = false
                        scope.launch {
                            val lineItemsList = order.lineItems?.elements ?: emptyList()
                            val products = lineItemsList.flatMap { li ->
                                val name = li.item?.name?.takeIf { it.isNotBlank() }
                                    ?: li.name.takeIf { it.isNotBlank() } ?: "(Không tên)"
                                val qty = li.quantity.roundToInt().coerceAtLeast(1)
                                List(qty) { idx -> Product(id = li.id.hashCode() + idx, name = name, price = li.price / 100.0, category = "Clover") }
                            }
                            val td       = order.total / 100.0
                            val subtotal = td / 1.08
                            val tax      = td - subtotal
                            val now      = java.util.Calendar.getInstance()
                            val dateStr  = SimpleDateFormat("MM-dd-yyyy", Locale.US).format(now.time)
                            val timeNow  = SimpleDateFormat("HH:mm", Locale.US).format(now.time)
                            val invoiceId = InvoiceStorage.generateInvoiceId()

                            InvoiceStorage.saveInvoice(context, Invoice(
                                id = invoiceId, items = products,
                                subtotal = subtotal, discount = 0.0, tax = tax,
                                total = td, date = dateStr, time = timeNow,
                                tableTitle = order.title
                            ))

                            val receiptText = buildCloverReceiptText(
                                invoiceId    = invoiceId,
                                tableTitle   = order.title,
                                lineItems    = lineItemsList,
                                totalCents   = order.total,
                                receivedAmt  = received,
                                changeAmt    = change,
                                date         = dateStr,
                                time         = timeNow
                            )
                            val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                            val printMsg = try {
                                SocketPrinter.printText(ip, port, receiptText)
                                "✅ In thành công · Hóa đơn $invoiceId"
                            } catch (e: Exception) {
                                "⚠️ Đã lưu hóa đơn $invoiceId\n❌ Lỗi máy in: ${e.message}"
                            }
                            val deleteMsg = CloverRepository.deleteOrderViaProxy(
                                CloverConfig.PROXY_SECRET, order.id
                            ).fold(
                                onSuccess = { "✅ Đã release bàn ${order.title} trên Clover POS" },
                                onFailure = { "⚠️ Không thể xoá order Clover: ${it.message}" }
                            )
                            payResult = "$printMsg\n$deleteMsg"
                            showPayDialog = true
                        }
                    },
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("🖨️ Xác nhận & In bill", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeCalc = false }) { Text("Hủy") }
            }
        )
    }

    // Cash payment result dialog
    if (showPayDialog) {
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            confirmButton = {
                TextButton(onClick = { showPayDialog = false; onCloseTable() }) {
                    Text("OK · Đóng bàn")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPayDialog = false }) { Text("Ở lại") }
            },
            title = { Text("Thanh toán Cash") },
            text  = { Text(payResult) }
        )
    }

}

private fun formatCents(cents: Long): String {
    val dollars   = cents / 100
    val remainder = cents % 100
    return "$%d.%02d".format(dollars, remainder)
}

private fun buildCloverReceiptText(
    invoiceId: String,
    tableTitle: String,
    lineItems: List<CloverLineItem>,
    totalCents: Long,
    receivedAmt: Double = 0.0,
    changeAmt: Double = 0.0,
    date: String,
    time: String
): String {
    val sb = StringBuilder()
    sb.appendLine("                  STONE PHO POS")
    sb.appendLine("          1525 Baytree Rd, ste M, Valdosta, GA ")
    sb.appendLine("------------------------------------------------")
    sb.appendLine("Invoice ID: $invoiceId")
    if (tableTitle.isNotBlank()) sb.appendLine("Table: $tableTitle")
    sb.appendLine("Date: $date               Time: $time")
    sb.appendLine("------------------------------------------------")
    lineItems.forEach { li ->
        val name = (li.item?.name?.takeIf { it.isNotBlank() } ?: li.name.takeIf { it.isNotBlank() } ?: "(Không tên)").take(26)
        val qty  = if (li.quantity % 1.0 == 0.0) li.quantity.toInt().toString() else "%.1f".format(li.quantity)
        val amt  = "%.2f$".format(li.lineTotal / 100.0)
        sb.appendLine("$name x$qty $amt")
    }
    val totalDollars = totalCents / 100.0
    val subtotal     = totalDollars / 1.08
    val tax          = totalDollars - subtotal
    sb.appendLine("------------------------------------------------")
    sb.appendLine("Subtotal:                        %.2f$".format(subtotal))
    sb.appendLine("Tax (8%%):                       %.2f$".format(tax))
    sb.appendLine("TOTAL:                           %.2f$".format(totalDollars))
    if (receivedAmt > 0.0) {
        sb.appendLine("------------------------------------------------")
        sb.appendLine("Cash received:                   %.2f$".format(receivedAmt))
        sb.appendLine("Change:                          %.2f$".format(changeAmt))
    }
    sb.appendLine("------------------------------------------------")
    sb.appendLine("                  Thank you! \n\n\n")
    return sb.toString()
}
