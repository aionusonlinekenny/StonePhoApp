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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import com.example.stonephopro.utils.print.KitchenPrinterConfig
import com.example.stonephopro.viewmodel.OrderViewModel
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
private const val PREFS_NAME    = "clover_local"
private const val KEY_CLOSED    = "closed_tables"
private const val KEY_ORDER_TBL = "order_table_map"   // orderId → tableTitle
private const val SIXTEEN_HOURS = 16L * 60 * 60 * 1000

private fun loadClosedTables(ctx: Context): Map<String, Long> {
    val json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CLOSED, null) ?: return emptyMap()
    return try {
        val type = object : TypeToken<Map<String, Long>>() {}.type
        val raw: Map<String, Long>? = Gson().fromJson(json, type)
        val cutoff = System.currentTimeMillis() - SIXTEEN_HOURS
        raw?.filter { it.value > cutoff } ?: emptyMap()
    } catch (e: Exception) { emptyMap() }
}

private fun saveClosedTables(ctx: Context, tables: Map<String, Long>) {
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_CLOSED, Gson().toJson(tables)).apply()
}

// Lưu mapping orderId → tableTitle khi tạo order qua REST (Clover có thể không trả title)
private fun saveOrderTableMapping(ctx: Context, orderId: String, tableTitle: String) {
    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val type  = object : TypeToken<MutableMap<String, String>>() {}.type
    val map: MutableMap<String, String> = try {
        Gson().fromJson(prefs.getString(KEY_ORDER_TBL, null), type) ?: mutableMapOf()
    } catch (e: Exception) { mutableMapOf() }
    map[orderId] = tableTitle
    prefs.edit().putString(KEY_ORDER_TBL, Gson().toJson(map)).apply()
}

private fun removeOrderTableMapping(ctx: Context, orderId: String) {
    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val type  = object : TypeToken<MutableMap<String, String>>() {}.type
    val map: MutableMap<String, String> = try {
        Gson().fromJson(prefs.getString(KEY_ORDER_TBL, null), type) ?: return
    } catch (e: Exception) { return }
    map.remove(orderId)
    prefs.edit().putString(KEY_ORDER_TBL, Gson().toJson(map)).apply()
}

private fun loadOrderTableMapping(ctx: Context): Map<String, String> {
    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val type  = object : TypeToken<Map<String, String>>() {}.type
    return try {
        Gson().fromJson(prefs.getString(KEY_ORDER_TBL, null), type) ?: emptyMap()
    } catch (e: Exception) { emptyMap() }
}

// ── Main screen ───────────────────────────────────────────────────────────────
@Composable
fun CloverOrderScreen(viewModel: OrderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var openOrders        by remember { mutableStateOf<List<CloverOrder>>(emptyList()) }
    var isFirstLoad       by remember { mutableStateOf(true) }
    var isRefreshing      by remember { mutableStateOf(false) }
    var errorMsg          by remember { mutableStateOf("") }
    var selectedOrder     by remember { mutableStateOf<CloverOrder?>(null) }
    var selectedTab        by remember { mutableStateOf(0) }
    var closedTables       by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var todayCashTotal     by remember { mutableStateOf(0.0) }
    var selectedSlotTitle  by remember { mutableStateOf<String?>(null) }
    var showAddItemsEmpty  by remember { mutableStateOf(false) }
    // Dine In orderType ID — extracted from loaded orders so new orders appear on Clover Dining POS
    var dineInOrderTypeId  by remember { mutableStateOf<String?>(null) }

    // Load locally closed tables + pre-fetch Dine In orderType ID on start
    LaunchedEffect(Unit) {
        closedTables = loadClosedTables(context)
        // Pre-fetch so dineInOrderTypeId is ready even when no tables are open yet
        CloverRepository.fetchOrderTypesViaProxy(CloverConfig.PROXY_SECRET).onSuccess { types ->
            dineInOrderTypeId = types.firstOrNull { t ->
                val l = t.label.lowercase()
                l.contains("dine") || l.contains("table") || l.contains("dining")
            }?.id
        }
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
        // Clean up any local orderId→title mapping for orders of this table
        openOrders.filter { it.title.trim() == tableTitle.trim() }
                  .forEach { removeOrderTableMapping(context, it.id) }
        openOrders    = openOrders.filter { it.title.trim() != tableTitle.trim() }
        selectedOrder = null
    }

    fun applyFilter(orders: List<CloverOrder>): List<CloverOrder> {
        // PHP pre-filters: paid orders, closed/deleted state, older than 8h.
        // Android only handles local cash closings (SharedPreferences).
        val afterCashClose = orders.filter { o ->
            val closeTime = closedTables[o.title.trim()]
            closeTime == null || o.createdTime > closeTime
        }
        // Dining tables (non-empty title, Dine In type): keep only newest per title
        // to avoid duplicate slots on the floor plan after re-open.
        // To Go orders: keep ALL — multiple concurrent To Go orders are normal.
        val diningOrders = afterCashClose
            .filter { o ->
                val title = o.title.trim()
                val type  = o.orderType?.label?.lowercase() ?: ""
                title.isNotBlank() && !type.contains("go") && !type.contains("takeout") && !type.contains("pickup")
            }
            .groupBy { it.title.trim() }
            .mapValues { (_, list) -> list.maxByOrNull { it.createdTime }!! }
            .values.toList()
        val toGoOrders = afterCashClose.filter { o ->
            val title = o.title.trim()
            val type  = o.orderType?.label?.lowercase() ?: ""
            title.isEmpty() || type.contains("go") || type.contains("takeout") || type.contains("pickup")
        }
        return diningOrders + toGoOrders
    }

    // Background refresh — updates data silently, no loading flash
    fun reload() {
        isRefreshing = true
        scope.launch {
            CloverRepository.fetchOpenOrdersViaProxy(CloverConfig.PROXY_SECRET)
                .onSuccess { orders ->
                    // Enrich orders whose title is blank using local orderId→tableTitle mapping
                    // (REST-created orders may not have title set in Clover's response)
                    val localMapping = loadOrderTableMapping(context)
                    val enriched = if (localMapping.isEmpty()) orders else orders.map { o ->
                        if (o.title.isBlank()) o.copy(title = localMapping[o.id] ?: "") else o
                    }
                    openOrders = applyFilter(enriched)
                    errorMsg   = ""
                    // Extract Dine In orderType ID from any loaded order that has one
                    if (dineInOrderTypeId == null) {
                        dineInOrderTypeId = enriched.firstNotNullOfOrNull { o ->
                            val label = o.orderType?.label?.lowercase() ?: ""
                            if (label.contains("dine") || label.contains("table") || label.contains("dining"))
                                o.orderType?.id
                            else null
                        }
                    }
                }
                .onFailure { err ->
                    if (isFirstLoad) errorMsg = "Lỗi backend: ${err.message}"
                }
            // Tổng cash đã thu hôm nay từ InvoiceStorage (giống Invoice Check)
            val today = InvoiceStorage.getTodayDate()
            todayCashTotal = InvoiceStorage.loadInvoices(context, today).sumOf { it.total }
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
                if (!isFirstLoad) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0D1B2A), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "💵 Cash hôm nay: ${"$%,.2f".format(todayCashTotal)}",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Magenta, Color.Cyan)
                                ),
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        )
                    }
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

            // Empty-table: Add Items dialog (no existing order — create new)
            if (showAddItemsEmpty && selectedSlotTitle != null) {
                AddItemsToKitchenDialog(
                    tableTitle      = selectedSlotTitle!!,
                    existingOrderId = null,
                    dineInOrderTypeId = dineInOrderTypeId,
                    viewModel       = viewModel,
                    onDismiss       = { showAddItemsEmpty = false; selectedSlotTitle = null },
                    onOrderCreated  = { reload() }
                )
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
                            tableOrderMap     = tableOrderMap,
                            selectedOrder     = selectedOrder,
                            selectedSlotTitle = selectedSlotTitle,
                            onTableClick      = { order, slotTitle ->
                                if (order != null) {
                                    selectedOrder     = if (selectedOrder?.id == order.id) null else order
                                    selectedSlotTitle = null
                                } else {
                                    selectedOrder = null
                                    if (selectedSlotTitle == slotTitle) {
                                        selectedSlotTitle = null
                                    } else {
                                        selectedSlotTitle = slotTitle
                                        showAddItemsEmpty = true
                                    }
                                }
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
                            viewModel      = viewModel,
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
    selectedSlotTitle: String?,
    onTableClick: (CloverOrder?, String) -> Unit
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
                            val isSelected = (order != null && selectedOrder?.id == order.id) ||
                                             (order == null && slot.cloverTitle == selectedSlotTitle)
                            TableCell(
                                label      = slot.name,
                                seats      = slot.seats,
                                isOccupied = order != null,
                                isSelected = isSelected,
                                total      = order?.total,
                                onClick    = { onTableClick(order, slot.cloverTitle) },
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
    viewModel: OrderViewModel,
    onClose: () -> Unit,
    onCloseTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var payResult          by remember { mutableStateOf("") }
    var showPayDialog      by remember { mutableStateOf(false) }
    var showChangeCalc     by remember { mutableStateOf(false) }
    var showSplitBill      by remember { mutableStateOf(false) }
    var showAddItems       by remember { mutableStateOf(false) }
    var receivedAmountText by remember { mutableStateOf("") }
    var pendingReceiptText by remember { mutableStateOf("") }   // built on confirm, printed on demand

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

            // ── Add items to kitchen ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Button3D(
                    text = "➕ Thêm món & In bếp",
                    onClick = { showAddItems = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    gradientColors = listOf(Color(0xFFEF6C00), Color(0xFFBF360C)),
                    fontSize = 13.sp
                )
            }

            // ── Buttons ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button3D(
                    text = "✂️ Chia bill",
                    onClick = { showSplitBill = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                    fontSize = 13.sp,
                    gradientColors = listOf(Color(0xFF7B1FA2), Color(0xFF4A148C))
                )
                Button3D(
                    text = "💵 Cash",
                    onClick = { receivedAmountText = ""; showChangeCalc = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                    fontSize = 14.sp,
                    gradientColors = listOf(Color(0xFF43A047), Color(0xFF1B5E20))
                )
            }
        }
    }

    // ── Change calculator dialog ──────────────────────────────────────────────
    if (showChangeCalc) {
        val totalDollars      = order.total / 100.0
        val received          = receivedAmountText.toDoubleOrNull() ?: 0.0
        val effectiveReceived = if (receivedAmountText.isEmpty()) totalDollars else received
        val effectiveChange   = effectiveReceived - totalDollars
        val canConfirm        = receivedAmountText.isEmpty() || received >= totalDollars

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
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cần thanh toán", fontSize = 16.sp, color = Color(0xFF1565C0))
                        Text(
                            formatCents(order.total),
                            fontSize = 30.sp,
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
                        label = { Text("Tiền nhận từ khách ($)", fontSize = 16.sp) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 26.sp,
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
                            fontSize = 16.sp,
                            color = if (canConfirm) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text(
                            text = when {
                                receivedAmountText.isEmpty() -> "Trả đúng tiền"
                                canConfirm -> "$%,.2f".format(effectiveChange)
                                else -> "-$%,.2f".format(-effectiveChange)
                            },
                            fontSize = if (receivedAmountText.isEmpty()) 18.sp else 32.sp,
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
                                receivedAmt  = effectiveReceived,
                                changeAmt    = effectiveChange,
                                date         = dateStr,
                                time         = timeNow
                            )
                            pendingReceiptText = receiptText
                            val deleteMsg = CloverRepository.deleteOrderViaProxy(
                                CloverConfig.PROXY_SECRET, order.id
                            ).fold(
                                onSuccess = { "✅ Đã release bàn ${order.title} trên Clover POS" },
                                onFailure = { "⚠️ Không thể xoá order Clover: ${it.message}" }
                            )
                            payResult = "✅ Hóa đơn $invoiceId đã lưu\n$deleteMsg"
                            showPayDialog = true
                        }
                    },
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("✅ Xác nhận thanh toán", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeCalc = false }) { Text("Hủy") }
            }
        )
    }

    // ── Split bill dialog ─────────────────────────────────────────────────────
    if (showSplitBill) {
        SplitBillDialog(
            order        = order,
            onDismiss    = { showSplitBill = false },
            onCloseTable = { showSplitBill = false; onCloseTable() }
        )
    }

    // Cash payment result dialog — offer optional receipt printing
    if (showPayDialog) {
        var isPrinting by remember { mutableStateOf(false) }
        var printResult by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("✅ Thanh toán Cash", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(payResult, fontSize = 13.sp)
                    if (printResult.isNotEmpty()) {
                        Text(printResult, fontSize = 12.sp,
                            color = if (printResult.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("In receipt cho khách?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showPayDialog = false; onCloseTable() }) {
                        Text("Không in", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (!isPrinting) {
                                isPrinting = true
                                val txt = pendingReceiptText
                                scope.launch {
                                    val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                                    printResult = try {
                                        SocketPrinter.printText(ip, port, txt)
                                        "✅ Đã in"
                                    } catch (e: Exception) { "❌ ${e.message?.take(50)}" }
                                    isPrinting = false
                                    if (printResult.startsWith("✅")) { showPayDialog = false; onCloseTable() }
                                }
                            }
                        },
                        enabled = !isPrinting,
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Text(if (isPrinting) "⏳ Đang in…" else "🖨️ In receipt")
                    }
                }
            },
            dismissButton = {}
        )
    }

    // ── Add items to kitchen dialog ───────────────────────────────────────────
    if (showAddItems) {
        AddItemsToKitchenDialog(
            tableTitle      = order.title.ifBlank { order.id.takeLast(6) },
            existingOrderId = order.id,
            viewModel       = viewModel,
            onDismiss       = { showAddItems = false }
        )
    }

}

// ── Split bill dialog ─────────────────────────────────────────────────────────
@Composable
private fun SplitBillDialog(
    order: CloverOrder,
    onDismiss: () -> Unit,
    onCloseTable: () -> Unit
) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val allItems = order.lineItems?.elements ?: emptyList()

    // Mode: 0 = by item, 1 = by amount
    var splitMode by remember { mutableStateOf(0) }

    // --- By-item state ---
    var remainingItems by remember { mutableStateOf(allItems) }
    var selectedIds    by remember { mutableStateOf<Set<String>>(emptySet()) }

    // --- By-amount state ---
    var remainingCents  by remember { mutableStateOf(order.total) }
    var splitAmountText by remember { mutableStateOf("") }

    var splitCount          by remember { mutableStateOf(1) }
    var printStatus         by remember { mutableStateOf("") }
    var isSaving            by remember { mutableStateOf(false) }
    // By-amount: pending confirmation before print/skip
    var showAmtConfirm      by remember { mutableStateOf(false) }
    var confirmedSplitCents by remember { mutableStateOf(0L) }

    val selectedItems = remainingItems.filter { it.id in selectedIds }
    val selectedTotal = selectedItems.sumOf { it.lineTotal }
    val allDoneItem   = remainingItems.isEmpty()
    val allDoneAmount = remainingCents <= 0L
    val allDone       = if (splitMode == 0) allDoneItem else allDoneAmount

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape    = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Text(
                    "✂️ Chia bill — Bàn ${order.title.ifEmpty { "#${order.id.takeLast(4)}" }}",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Mode tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3E5F5), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("📋 Theo món", "💵 Theo số tiền").forEachIndexed { idx, label ->
                        val active = splitMode == idx
                        Button(
                            onClick = {
                                splitMode = idx
                                printStatus = ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) Color(0xFF7B1FA2) else Color.Transparent,
                                contentColor   = if (active) Color.White else Color(0xFF7B1FA2)
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) { Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (allDone) {
                    // ── Tất cả đã tính ────────────────────────────────────
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✅", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Đã tính hết ${allItems.size} món", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${splitCount - 1} phần đã in", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                } else if (splitMode == 0) {
                    // ── BY ITEM mode ──────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF7B1FA2).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Phần $splitCount", fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), fontSize = 15.sp)
                        Text("Còn ${remainingItems.size} món chưa tính", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = { selectedIds = remainingItems.map { it.id }.toSet() }) {
                            Text("Chọn tất cả", fontSize = 13.sp, color = Color(0xFF7B1FA2))
                        }
                        TextButton(onClick = { selectedIds = emptySet() }) {
                            Text("Bỏ chọn", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("",       modifier = Modifier.width(28.dp))
                        Text("Món",    modifier = Modifier.weight(1f),   fontSize = 11.sp, color = Color.Gray)
                        Text("T.Tiền", modifier = Modifier.width(72.dp), fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.End)
                    }
                    Divider()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(remainingItems, key = { it.id }) { li ->
                            val isSelected = li.id in selectedIds
                            val name = li.item?.name?.takeIf { it.isNotBlank() }
                                ?: li.name.takeIf { it.isNotBlank() } ?: "(Không tên)"
                            val qty = if (li.quantity % 1.0 == 0.0) "x${li.quantity.toInt()}"
                                      else "x%.1f".format(li.quantity)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFFF3E5F5) else Color.Transparent)
                                    .clickable { selectedIds = if (isSelected) selectedIds - li.id else selectedIds + li.id }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isSelected) "✓" else "○", modifier = Modifier.width(28.dp),
                                    color = if (isSelected) Color(0xFF7B1FA2) else Color.LightGray,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(qty, fontSize = 11.sp, color = Color.Gray)
                                }
                                Text(formatCents(li.lineTotal), modifier = Modifier.width(72.dp),
                                    fontSize = 13.sp, textAlign = TextAlign.End,
                                    color = if (isSelected) Color(0xFF7B1FA2) else Color(0xFF424242))
                            }
                            Divider(color = Color(0xFFEEEEEE))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF7B1FA2), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("${selectedItems.size} món · Phần $splitCount", color = Color.White, fontSize = 13.sp)
                        Text(formatCents(selectedTotal), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }

                } else {
                    // ── BY AMOUNT mode ────────────────────────────────────
                    val splitDollars = splitAmountText.toDoubleOrNull() ?: 0.0
                    val splitCents   = (splitDollars * 100).toLong()
                    val canSplitAmt  = splitCents in 1..remainingCents

                    // Total remaining
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF7B1FA2).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Còn lại cần tính", fontSize = 14.sp, color = Color(0xFF7B1FA2))
                        Text(formatCents(remainingCents), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Amount input
                    OutlinedTextField(
                        value = splitAmountText,
                        onValueChange = { v ->
                            if (v.isEmpty() || v.matches(Regex("^\\d{0,6}(\\.\\d{0,2})?$"))) splitAmountText = v
                        },
                        label = { Text("Số tiền người này trả ($)", fontSize = 13.sp) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // After this split, what's left?
                    if (splitCents > 0) {
                        val afterCents = remainingCents - splitCents
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    if (afterCents >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                if (afterCents > 0) "Còn lại (trả thẻ POS)" else if (afterCents == 0L) "Trả đủ ✅" else "Vượt quá",
                                fontSize = 13.sp,
                                color = if (afterCents >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            Text(
                                if (afterCents >= 0) formatCents(afterCents) else "-${formatCents(-afterCents)}",
                                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = if (afterCents >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Summary row
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (canSplitAmt) Color(0xFF7B1FA2) else Color(0xFFBDBDBD), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Phần $splitCount", color = Color.White, fontSize = 13.sp)
                        Text(if (canSplitAmt) formatCents(splitCents) else "--", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                // Print status
                if (printStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        printStatus, fontSize = 12.sp,
                        color = if (printStatus.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button3D(
                        text = "✕ Hủy", onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp), fontSize = 13.sp,
                        gradientColors = listOf(Color(0xFF78909C), Color(0xFF546E7A))
                    )

                    if (!allDone) {
                        if (splitMode == 0) {
                            // BY ITEM — print immediately
                            val canPrint = selectedIds.isNotEmpty()
                            Button3D(
                                text    = "🖨️ In phần $splitCount & tiếp",
                                onClick = {
                                    if (canPrint) scope.launch {
                                        val now     = java.util.Calendar.getInstance()
                                        val receipt = buildCloverReceiptText(
                                            invoiceId  = "SPLIT-$splitCount",
                                            tableTitle = order.title,
                                            lineItems  = selectedItems,
                                            totalCents = selectedTotal,
                                            date = SimpleDateFormat("MM-dd-yyyy", Locale.US).format(now.time),
                                            time = SimpleDateFormat("HH:mm", Locale.US).format(now.time)
                                        )
                                        val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                                        printStatus = try {
                                            SocketPrinter.printText(ip, port, receipt)
                                            "✅ Đã in Phần $splitCount"
                                        } catch (e: Exception) { "❌ Lỗi: ${e.message}" }
                                        remainingItems = remainingItems.filter { it.id !in selectedIds }
                                        selectedIds    = emptySet()
                                        splitCount++
                                    }
                                },
                                modifier       = Modifier.weight(2f).height(46.dp),
                                fontSize       = 12.sp,
                                gradientColors = if (canPrint) listOf(Color(0xFF7B1FA2), Color(0xFF4A148C))
                                                 else listOf(Color(0xFFBDBDBD), Color(0xFF9E9E9E))
                            )
                        } else {
                            // BY AMOUNT — confirm → ask print → record on Clover
                            val sc       = ((splitAmountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                            val canSplit = sc in 1L..remainingCents
                            Button3D(
                                text    = "💳 Thanh toán phần $splitCount & tiếp",
                                onClick = {
                                    if (canSplit) {
                                        confirmedSplitCents = sc
                                        showAmtConfirm      = true
                                    }
                                },
                                modifier       = Modifier.weight(2f).height(46.dp),
                                fontSize       = 12.sp,
                                gradientColors = if (canSplit) listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                                                 else listOf(Color(0xFFBDBDBD), Color(0xFF9E9E9E))
                            )
                        }
                    }

                    Button3D(
                        text    = if (isSaving) "⏳..." else "✔ Đóng bàn",
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                scope.launch {
                                    val lineItemsList = order.lineItems?.elements ?: emptyList()
                                    val products = lineItemsList.flatMap { li ->
                                        val name = li.item?.name?.takeIf { it.isNotBlank() }
                                            ?: li.name.takeIf { it.isNotBlank() } ?: "(Không tên)"
                                        val qty = li.quantity.roundToInt().coerceAtLeast(1)
                                        List(qty) { idx -> Product(id = li.id.hashCode() + idx, name = name, price = li.price / 100.0, category = "Clover") }
                                    }
                                    val td  = order.total / 100.0
                                    val now = java.util.Calendar.getInstance()
                                    InvoiceStorage.saveInvoice(context, Invoice(
                                        id         = InvoiceStorage.generateInvoiceId(),
                                        items      = products,
                                        subtotal   = td / 1.08, discount = 0.0, tax = td - td / 1.08,
                                        total      = td,
                                        date       = SimpleDateFormat("MM-dd-yyyy", Locale.US).format(now.time),
                                        time       = SimpleDateFormat("HH:mm",      Locale.US).format(now.time),
                                        tableTitle = order.title
                                    ))
                                    CloverRepository.deleteOrderViaProxy(CloverConfig.PROXY_SECRET, order.id)
                                    isSaving = false
                                    onCloseTable()
                                }
                            }
                        },
                        modifier       = Modifier.weight(1f).height(46.dp),
                        fontSize       = 12.sp,
                        gradientColors = listOf(Color(0xFF43A047), Color(0xFF1B5E20))
                    )
                }
            }
        }
    }

    // ── By-amount: print confirmation dialog ──────────────────────────────────
    if (showAmtConfirm) {
        var isProcessing by remember { mutableStateOf(false) }
        var amtStatus    by remember { mutableStateOf("") }

        // Shared action: record partial cash on Clover (negative line item) + update local state
        suspend fun processPayment(doPrint: Boolean, ip: String, port: Int) {
            val now     = java.util.Calendar.getInstance()
            val dateStr = SimpleDateFormat("MM-dd-yyyy", Locale.US).format(now.time)
            val timeStr = SimpleDateFormat("HH:mm",      Locale.US).format(now.time)

            // Record partial cash as a negative credit on Clover order so POS shows updated balance
            val creditResult = CloverRepository.addLineItemViaProxy(
                CloverConfig.PROXY_SECRET,
                order.id,
                "Cash Paid - Part $splitCount",
                -confirmedSplitCents,
                1000
            )
            amtStatus = if (creditResult.isSuccess) "✅ Đã cập nhật Clover POS"
                        else "⚠️ Clover: ${creditResult.exceptionOrNull()?.message?.take(40)}"

            if (doPrint) {
                val ticket = buildString {
                    appendLine("  STONE PHO")
                    appendLine("Table: ${order.title.ifEmpty { "#${order.id.takeLast(4)}" }}")
                    appendLine("Date : $dateStr  $timeStr")
                    appendLine("----------------------------")
                    appendLine("SPLIT BILL — Phần $splitCount")
                    appendLine("----------------------------")
                    appendLine("Cash Paid  : ${formatCents(confirmedSplitCents)}")
                    val after = remainingCents - confirmedSplitCents
                    if (after > 0) appendLine("Remaining  : ${formatCents(after)}  (pay by card/POS)")
                    appendLine("============================")
                    appendLine(); appendLine(); appendLine()
                }
                amtStatus += try {
                    SocketPrinter.printText(ip, port, ticket)
                    "\n✅ Đã in receipt"
                } catch (e: Exception) { "\n❌ Printer: ${e.message?.take(40)}" }
            }

            remainingCents  -= confirmedSplitCents
            splitAmountText  = ""
            splitCount++
            printStatus = "✅ Phần ${splitCount - 1}: ${formatCents(confirmedSplitCents)} cash"
        }

        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("💳 Thanh toán phần $splitCount", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF1565C0).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cash nhận", fontSize = 14.sp, color = Color(0xFF1565C0))
                        Text(formatCents(confirmedSplitCents), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                    val after = remainingCents - confirmedSplitCents
                    if (after > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Còn lại (thẻ POS)", fontSize = 13.sp, color = Color(0xFF2E7D32))
                            Text(formatCents(after), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                    if (amtStatus.isNotEmpty()) {
                        Text(amtStatus, fontSize = 11.sp,
                            color = if (amtStatus.contains("✅")) Color(0xFF2E7D32) else Color(0xFFC62828))
                    }
                    if (!isProcessing) Text("In receipt cho phần này?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                if (!isProcessing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            isProcessing = true
                            val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                            scope.launch {
                                processPayment(doPrint = false, ip, port)
                                showAmtConfirm = false
                            }
                        }) { Text("Bỏ qua in", color = Color.Gray) }
                        Button(
                            onClick = {
                                isProcessing = true
                                val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                                scope.launch {
                                    processPayment(doPrint = true, ip, port)
                                    showAmtConfirm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) { Text("🖨️ In receipt") }
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
private fun AddItemsToKitchenDialog(
    tableTitle: String,           // display name / Clover title of the table
    existingOrderId: String?,     // null = create a new Clover order first
    dineInOrderTypeId: String? = null,  // Dine In orderType ID so new orders show on Clover POS
    viewModel: OrderViewModel,
    onDismiss: () -> Unit,
    onOrderCreated: (() -> Unit)? = null  // called after new order created → triggers reload
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val categories = viewModel.categories
    var selectedTab by remember { mutableStateOf(0) }
    val kitchenCart = remember { mutableStateListOf<Pair<com.example.stonephopro.model.Product, Int>>() }
    var printStatus by remember { mutableStateOf("") }
    var isPrinting  by remember { mutableStateOf(false) }

    val currentCatProducts = remember(selectedTab, categories) {
        if (categories.isEmpty()) emptyList()
        else viewModel.getAllProducts().filter { it.category == categories.getOrNull(selectedTab)?.name }
    }

    val cartTotal = kitchenCart.sumOf { (p, q) -> p.price * q }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEF6C00))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "➕ Thêm món — Bàn $tableTitle",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }

                // Banner thông tin khi bàn trống
                if (existingOrderId == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE3F2FD))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ℹ️ Bàn trống — App sẽ tạo order trên Clover và hiện xanh sau vài giây. Clover Dining POS không hiện bàn này (giới hạn API).",
                            fontSize = 12.sp,
                            color = Color(0xFF0D47A1),
                            lineHeight = 17.sp
                        )
                    }
                }

                // Category tabs
                if (categories.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEachIndexed { idx, cat ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                text = { Text(cat.name, fontSize = 13.sp) }
                            )
                        }
                    }
                }

                // Product list
                LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
                    items(currentCatProducts) { prod ->
                        val qty = kitchenCart.find { it.first.id == prod.id }?.second ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${"$%.2f".format(prod.price)}", fontSize = 12.sp, color = Color(0xFF1565C0))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (qty > 0) {
                                    IconButton(onClick = {
                                        val idx = kitchenCart.indexOfFirst { it.first.id == prod.id }
                                        if (idx >= 0) {
                                            if (kitchenCart[idx].second <= 1) kitchenCart.removeAt(idx)
                                            else kitchenCart[idx] = kitchenCart[idx].copy(second = kitchenCart[idx].second - 1)
                                        }
                                    }, modifier = Modifier.size(32.dp)) { Text("➖", fontSize = 16.sp) }
                                    Text("$qty", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                                }
                                IconButton(onClick = {
                                    val idx = kitchenCart.indexOfFirst { it.first.id == prod.id }
                                    if (idx >= 0) kitchenCart[idx] = kitchenCart[idx].copy(second = kitchenCart[idx].second + 1)
                                    else kitchenCart.add(Pair(prod, 1))
                                }, modifier = Modifier.size(32.dp)) { Text("➕", fontSize = 16.sp) }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                    }
                }

                // Cart summary + print
                if (kitchenCart.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3E0))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        kitchenCart.forEach { (prod, qty) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${qty}x ${prod.name}", fontSize = 12.sp)
                                Text("${"$%.2f".format(prod.price * qty)}", fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider()
                        Text("Tổng: ${"$%.2f".format(cartTotal)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                if (printStatus.isNotEmpty()) {
                    Text(
                        printStatus,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        fontSize  = 12.sp,
                        color     = Color(0xFF424242),
                        lineHeight = 18.sp
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button3D(
                        text = "Hủy",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        gradientColors = listOf(Color(0xFF78909C), Color(0xFF546E7A)),
                        fontSize = 13.sp
                    )
                    val canPrint = kitchenCart.isNotEmpty() && !isPrinting
                    Button3D(
                        text = if (isPrinting) "⏳ Đang in..." else "🖨️ In bếp",
                        onClick = {
                            if (!canPrint) return@Button3D
                            isPrinting = true
                            printStatus = ""
                            scope.launch {
                                try {
                                    val steps = mutableListOf<String>()

                                    // ── STEP 1: Resolve / create Clover order ─────
                                    val orderId: String = if (existingOrderId != null) {
                                        steps.add("📋 Order hiện có: …${existingOrderId.takeLast(8)}")
                                        existingOrderId
                                    } else {
                                        val typeInfo = if (dineInOrderTypeId != null) " (Dine In)" else " (no orderType)"
                                        steps.add("🔄 Tạo order Clover cho bàn $tableTitle$typeInfo…")
                                        printStatus = steps.joinToString("\n")
                                        val newId = CloverRepository.createOrderViaProxy(
                                            CloverConfig.PROXY_SECRET, tableTitle, dineInOrderTypeId
                                        ).getOrElse { e ->
                                            steps.add("❌ Tạo order thất bại: ${e.message?.take(80)}")
                                            printStatus = steps.joinToString("\n")
                                            isPrinting = false
                                            return@launch
                                        }
                                        steps.add("✅ Order tạo OK · ID: …${newId.takeLast(8)}")
                                        saveOrderTableMapping(context, newId, tableTitle)
                                        steps.add("💾 Lưu mapping local: $newId → $tableTitle")
                                        printStatus = steps.joinToString("\n")
                                        newId
                                    }

                                    // ── STEP 2: Group cart by kitchen zone ────────
                                    val grouped = mutableMapOf<KitchenPrinterConfig.Zone,
                                        MutableList<Pair<com.example.stonephopro.model.Product, Int>>>()
                                    for (item in kitchenCart) {
                                        val zone = KitchenPrinterConfig.getCategoryZone(context, item.first.category)
                                        grouped.getOrPut(zone) { mutableListOf() }.add(item)
                                    }
                                    steps.add("🗂️ ${kitchenCart.size} món → ${grouped.size} zone: ${grouped.keys.joinToString { it.label }}")
                                    printStatus = steps.joinToString("\n")

                                    // ── STEP 3: Print to each zone ────────────────
                                    var printedZones = 0
                                    for ((zone, items) in grouped) {
                                        val ipPort = KitchenPrinterConfig.getIpPort(context, zone)
                                        if (ipPort.isBlank()) {
                                            steps.add("⚠️ ${zone.label}: chưa cấu hình IP")
                                            printStatus = steps.joinToString("\n")
                                            continue
                                        }
                                        val parsed = KitchenPrinterConfig.parseIpPort(ipPort)
                                        if (parsed == null) {
                                            steps.add("⚠️ ${zone.label}: IP không hợp lệ ($ipPort)")
                                            printStatus = steps.joinToString("\n")
                                            continue
                                        }
                                        val (ip, port) = parsed
                                        steps.add("🔄 ${zone.label}: kết nối $ip:$port…")
                                        printStatus = steps.joinToString("\n")
                                        try {
                                            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
                                            val ticket = buildString {
                                                appendLine()
                                                appendLine("=== ${zone.emoji} ${zone.label} KITCHEN ===")
                                                appendLine("Table: $tableTitle")
                                                appendLine("Time : $timeStr")
                                                appendLine("----------------------------")
                                                for ((p, q) in items) appendLine("${q}x  ${p.name}")
                                                appendLine("============================")
                                                appendLine(); appendLine(); appendLine()
                                            }
                                            val byteCount = ticket.toByteArray(Charsets.US_ASCII).size
                                            SocketPrinter.printText(ip, port, ticket)
                                            printedZones++
                                            steps.add("✅ ${zone.label}: OK ($byteCount bytes gửi)")
                                        } catch (e: Exception) {
                                            steps.add("❌ ${zone.label} $ip:$port → ${e.javaClass.simpleName}: ${e.message?.take(60)}")
                                        }
                                        printStatus = steps.joinToString("\n")
                                    }

                                    // ── STEP 4: Add items to Clover order ─────────
                                    steps.add("🔄 Ghi ${kitchenCart.size} món vào Clover…")
                                    printStatus = steps.joinToString("\n")
                                    var cloverOk   = 0
                                    var cloverFail = 0
                                    for ((prod, qty) in kitchenCart) {
                                        CloverRepository.addLineItemViaProxy(
                                            CloverConfig.PROXY_SECRET,
                                            orderId,
                                            prod.name,
                                            (prod.price * 100).toLong(),
                                            qty * 1000
                                        ).fold(
                                            onSuccess = { cloverOk++ },
                                            onFailure = { e -> cloverFail++; steps.add("  ❌ ${prod.name}: ${e.message?.take(40)}") }
                                        )
                                    }
                                    when {
                                        cloverOk > 0 && cloverFail == 0 -> steps.add("✅ Clover: $cloverOk món đã ghi vào order …${orderId.takeLast(8)}")
                                        cloverFail > 0 -> steps.add("⚠️ Clover: $cloverOk OK · $cloverFail lỗi")
                                        else -> steps.add("⚠️ Clover: không ghi được")
                                    }
                                    printStatus = steps.joinToString("\n")

                                    // ── STEP 5: Cleanup & reload ──────────────────
                                    if (printedZones > 0) {
                                        kitchenCart.clear()
                                        if (existingOrderId == null) {
                                            steps.add("🔄 Reload floor plan…")
                                            printStatus = steps.joinToString("\n")
                                            onOrderCreated?.invoke()
                                            steps.add("✅ Bàn $tableTitle sẽ hiện xanh")
                                            printStatus = steps.joinToString("\n")
                                        }
                                    } else {
                                        steps.add("⚠️ Không in được bếp nào — kiểm tra IP trong Settings")
                                        printStatus = steps.joinToString("\n")
                                    }
                                } catch (e: Exception) {
                                    printStatus = "❌ Lỗi: ${e.message?.take(60)}"
                                } finally {
                                    isPrinting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(2f).height(48.dp),
                        gradientColors = if (canPrint) listOf(Color(0xFFEF6C00), Color(0xFFBF360C)) else listOf(Color(0xFF9E9E9E), Color(0xFF757575)),
                        fontSize = 13.sp
                    )
                }
            }
        }
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
