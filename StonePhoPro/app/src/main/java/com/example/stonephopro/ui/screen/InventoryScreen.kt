@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.stonephopro.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.model.InventoryItem
import com.example.stonephopro.viewmodel.InventoryViewModel
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.components.PriceInputDialog
import java.util.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.stonephopro.ui.components.DateSelectionDialog
import java.text.SimpleDateFormat
import com.example.stonephopro.utils.InventoryStorage
import android.content.Context

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun InventoryScreen(viewModel: InventoryViewModel, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val items = viewModel.items.filter {
        it.id.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button3D(text = "⬅", onClick = onBack)
            Spacer(Modifier.width(16.dp))
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f)) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("➕ Nhập hàng", fontSize = 16.sp, modifier = Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("📋 Danh sách", fontSize = 16.sp, modifier = Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("🔍 Thống kê", fontSize = 16.sp, modifier = Modifier.padding(12.dp)) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> InventoryEntryTab(viewModel)
            1 -> InventoryListTab(viewModel, items, searchQuery) { searchQuery = it }
            2 -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🔧 Đang phát triển tab Thống kê...", fontSize = 18.sp)
            }
        }
    }
}
@Composable
fun InventoryEntryTab(viewModel: InventoryViewModel) {
    val selectedDate = viewModel.selectedDate

    var qty by remember { mutableStateOf("") }
    var sl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var caseType by remember { mutableStateOf("") }
    var priceCents by remember { mutableIntStateOf(0) }
    var showPriceDialog by remember { mutableStateOf(false) }

    var selectedType by remember { mutableStateOf("DRY") }
    val options = listOf("DRY", "FRZ", "REF", "*")

    var unit by remember { mutableStateOf("CS") } // ✅ Default là CS
    var expandedUnit by remember { mutableStateOf(false) }
    val unitOptions = listOf("BOX", "CS", "BAG", "*")

    var expanded by remember { mutableStateOf(false) }

    val subtotal: Double = (sl.toIntOrNull() ?: 0) * (priceCents / 100.0)

    val suggestions = viewModel.items
        .map { it.id }
        .distinct()
        .filter { it.startsWith(qty) && qty.isNotEmpty() }

    var expandedSuggestions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = qty,
                    onValueChange = {
                        if (it.length <= 6) {
                            qty = it
                            expandedSuggestions = it.isNotEmpty()
                        }
                    },
                    label = { Text("Qty (6 số ID)") },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = expandedSuggestions && suggestions.isNotEmpty(),
                    onDismissRequest = { expandedSuggestions = false }
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                qty = suggestion
                                expandedSuggestions = false
                                val matchedItem = viewModel.items.findLast { it.id == suggestion }
                                if (matchedItem != null) {
                                    description = matchedItem.description
                                    caseType = matchedItem.caseType
                                    unit = matchedItem.unit
                                    selectedType = matchedItem.type
                                    priceCents = (matchedItem.price * 100).toInt()
                                }
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedType)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedType = type
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = sl,
                onValueChange = { if (it.length <= 2) sl = it },
                label = { Text("SL") },
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expandedUnit = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(unit)
                }
                DropdownMenu(
                    expanded = expandedUnit,
                    onDismissRequest = { expandedUnit = false }
                ) {
                    unitOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                unit = option
                                expandedUnit = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = caseType,
            onValueChange = { caseType = it },
            label = { Text("CT (50LB / 12/28oz / 4x1GAL...)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().clickable { showPriceDialog = true }) {
            OutlinedTextField(
                value = "%.2f".format(priceCents / 100.0),
                onValueChange = {},
                readOnly = true,
                label = { Text("Đơn giá") },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showPriceDialog) {
            PriceInputDialog(
                initialCents = priceCents,
                onDismiss = { showPriceDialog = false },
                onConfirm = {
                    priceCents = it
                    showPriceDialog = false
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Subtotal: %.2f$".format(subtotal), fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        Button3D(
            text = "➕ Nhập hàng",
            onClick = {
                val id = qty
                val quantity = sl.toIntOrNull() ?: 0
                val priceValue = priceCents / 100.0
                if (id.length == 6 && quantity > 0 && priceValue > 0.0) {
                    val existingSlc = viewModel.items
                        .filter { it.id == id && it.type == selectedType }
                        .sumOf { it.slc ?: 0 }

                    val item = InventoryItem(
                        id = id,
                        quantity = quantity,
                        unit = unit,
                        description = description,
                        caseType = caseType,
                        price = priceValue,
                        subtotal = quantity * priceValue,
                        date = selectedDate,
                        type = selectedType,
                        slc = existingSlc + quantity
                    )
                    viewModel.addItem(item)
                    qty = ""; sl = ""; unit = ""; description = ""; caseType = ""; priceCents = 0
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
    }
}
@Composable
fun InventoryListTab(
    viewModel: InventoryViewModel,
    filteredItems: List<InventoryItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {

    val formatter = remember { SimpleDateFormat("MM-dd-yyyy", Locale.US) }
    var startDate by remember { mutableStateOf(formatter.format(Date())) }
    var endDate by remember { mutableStateOf(formatter.format(Date())) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var selectedDayStart by remember { mutableStateOf("01") }
    var selectedMonthStart by remember { mutableStateOf("01") }
    var selectedYearStart by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var selectedDayEnd by remember { mutableStateOf("01") }
    var selectedMonthEnd by remember { mutableStateOf("01") }
    var selectedYearEnd by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    val allItemsForFiltering = remember { mutableStateListOf<InventoryItem>() }
    val dayOptions = (1..31).map { it.toString().padStart(2, '0') }
    val monthOptions = (1..12).map { it.toString().padStart(2, '0') }

    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var showSLCDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var confirmDeleteItem by remember { mutableStateOf<InventoryItem?>(null) }

    var selectedType by remember { mutableStateOf("Tất cả") }
    val typeOptions = listOf("Tất cả", "DRY", "FRZ", "REF", "*")
    var expandedType by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(startDate, endDate) {
        if (startDate != endDate) {
            allItemsForFiltering.clear()
            allItemsForFiltering.addAll(InventoryStorage.loadAllItems(context)) // ✅ Đúng

        } else {
            allItemsForFiltering.clear()
        }
    }
    val itemsToday = if (startDate != endDate) {
        allItemsForFiltering.filter {
            try {
                val date = formatter.parse(it.date)
                val start = formatter.parse(startDate)
                val end = formatter.parse(endDate)
                date != null && !date.before(start) && !date.after(end)
            } catch (e: Exception) {
                false
            }
        }
    } else {
        viewModel.items
    }

        .filter { it.id.contains(searchQuery, ignoreCase = true) }
        .filter { selectedType == "Tất cả" || it.type == selectedType }
        .distinctBy { it.id + it.type }

    val totalSL = itemsToday.sumOf { it.quantity }
    val totalSubtotal = itemsToday.sumOf { it.subtotal }
    val taxRate = 0.001481
    val taxAmount = totalSubtotal * taxRate
    val grandTotal = totalSubtotal + taxAmount

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button3D(
                text = "📅 Từ ngày: $startDate",
                onClick = { showStartDatePicker = true },
                modifier = Modifier.weight(1f)
            )
            Button3D(
                text = "📅 Đến ngày: $endDate",
                onClick = { showEndDatePicker = true },
                modifier = Modifier.weight(1f)
            )
            Button3D(
                text = "🔄 Hôm nay",
                onClick = {
                    val today = com.example.stonephopro.utils.InventoryStorage.getTodayDate()
                    viewModel.loadItemsForDate(today)
                },
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expandedType = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Loại: $selectedType")
                }
                DropdownMenu(
                    expanded = expandedType,
                    onDismissRequest = { expandedType = false }
                ) {
                    typeOptions.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedType = type
                                expandedType = false
                            }
                        )
                    }
                }
            }
        }

        if (showStartDatePicker) {
            DateSelectionDialog(
                title = "Chọn Từ Ngày",
                selectedDay = selectedDayStart,
                selectedMonth = selectedMonthStart,
                selectedYear = selectedYearStart,
                onDismiss = { showStartDatePicker = false },
                onConfirm = { day, month, year ->
                    startDate = "$month-$day-$year"
                    showStartDatePicker = false
                }
            )
        }

        if (showEndDatePicker) {
            DateSelectionDialog(
                title = "Chọn Đến Ngày",
                selectedDay = selectedDayEnd,
                selectedMonth = selectedMonthEnd,
                selectedYear = selectedYearEnd,
                onDismiss = { showEndDatePicker = false },
                onConfirm = { day, month, year ->
                    endDate = "$month-$day-$year"
                    showEndDatePicker = false
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🧾 Tổng SL: $totalSL", fontWeight = FontWeight.Bold)
            Text("💵 Subtotal: %.2f$".format(totalSubtotal), fontWeight = FontWeight.Bold)
            Text("💸 Thuế 0.14%%: %.2f$".format(taxAmount), fontWeight = FontWeight.Bold)
            Text("🧮 Grand Total: %.2f$".format(grandTotal), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Tìm theo Qty (ID)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(itemsToday) { item ->
                val totalSL = itemsToday
                    .filter { it.id == item.id && it.type == item.type }
                    .sumOf { it.quantity }

                val slcValue = item.slc ?: 0
                val slcColor = if (slcValue == 0) Color.Red else Color(0xFF388E3C)

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Loại: ${item.type} | ${item.id} - ${item.description} SL: $totalSL ${item.unit} | ${item.caseType}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    editingItem = item
                                    showEditDialog = true
                                }
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("SLC: ", fontWeight = FontWeight.Bold)
                            Text(
                                text = "$slcValue",
                                fontWeight = FontWeight.Bold,
                                color = slcColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("✏️", modifier = Modifier.clickable {
                                editingItem = item
                                showSLCDialog = true
                            }.padding(8.dp))
                        }

                        Text("❌", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable { confirmDeleteItem = item }
                                .padding(8.dp)
                        )
                    }
                    Divider()
                }
            }
        }

        if (confirmDeleteItem != null) {
            AlertDialog(
                onDismissRequest = { confirmDeleteItem = null },
                title = { Text("Xác nhận xoá") },
                text = { Text("Bạn có chắc muốn xoá mặt hàng này?\nID: ${confirmDeleteItem?.id}") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteItem(confirmDeleteItem!!)
                        confirmDeleteItem = null
                    }) {
                        Text("✔ Xoá")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteItem = null }) {
                        Text("❌ Hủy")
                    }
                }
            )
        }

        if (showEditDialog && editingItem != null) {
            EditInventoryDialog(
                item = editingItem!!,
                context = context,
                onDismiss = {
                    showEditDialog = false
                    editingItem = null
                },
                onConfirm = { updated ->

                    InventoryStorage.updateItem(context, updated)

                    // cập nhật trong danh sách tạm
                    val idx = allItemsForFiltering.indexOfFirst {
                        it.id == updated.id && it.date == updated.date && it.type == updated.type
                    }
                    if (idx >= 0) {
                        allItemsForFiltering[idx] = updated
                    }

                    showEditDialog = false
                    editingItem = null
                }

            )
        }

        if (showSLCDialog && editingItem != null) {
            val item = editingItem!!
            val initialQty = item.slc ?: 0
            QuantityInputDialog(
                initialQty = initialQty,
                onDismiss = {
                    showSLCDialog = false
                    editingItem = null
                },
                onConfirm = { newQty ->
                    val updated = item.copy(slc = newQty)
                    viewModel.updateItem(updated)
                    showSLCDialog = false
                    editingItem = null
                }
            )
        }
    }
}


@Composable
fun QuantityInputDialog(
    initialQty: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantity by remember { mutableIntStateOf(initialQty) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(quantity) }) {
                Text("✔ OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("❌ Hủy")
            }
        },
        title = { Text("Nhập số lượng còn lại") },
        text = {
            Column {
                OutlinedTextField(
                    value = quantity.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("SLC") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("←", "0", "AC")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            Button3D(
                                text = digit,
                                onClick = {
                                    quantity = when (digit) {
                                        "←" -> quantity / 10
                                        "AC" -> 0
                                        else -> (quantity * 10 + digit.toInt()).coerceAtMost(999999)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .height(50.dp),
                                fontSize = 18.sp,
                                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun EditInventoryDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (InventoryItem) -> Unit,
    context: Context,
) {
    var qty by remember { mutableStateOf(item.id) }
    var sl by remember { mutableStateOf(item.quantity.toString()) }
    var description by remember { mutableStateOf(item.description) }
    var caseType by remember { mutableStateOf(item.caseType) }
    var priceCents by remember { mutableIntStateOf((item.price * 100).toInt()) }

    var selectedType by remember { mutableStateOf(item.type) }
    val options = listOf("DRY", "FRZ", "REF", "*")

    var unit by remember { mutableStateOf(item.unit) }
    var expandedUnit by remember { mutableStateOf(false) }
    val unitOptions = listOf("BOX", "CS", "BAG", "*")

    var expandedType by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }

    val subtotal = (sl.toIntOrNull() ?: 0) * (priceCents / 100.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Sửa sản phẩm") },
        text = {
            Column {
                OutlinedTextField(
                    value = qty,
                    onValueChange = { if (it.length <= 6) qty = it },
                    label = { Text("Qty (ID)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sl,
                        onValueChange = { if (it.length <= 3) sl = it },
                        label = { Text("SL") },
                        modifier = Modifier.weight(1f)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedUnit = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(unit)
                        }
                        DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                            unitOptions.forEach {
                                DropdownMenuItem(
                                    text = { Text(it) },
                                    onClick = {
                                        unit = it
                                        expandedUnit = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = caseType,
                    onValueChange = { caseType = it },
                    label = { Text("CT (ví dụ: 50LB, 12/28oz...)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().clickable { showPriceDialog = true }) {
                    OutlinedTextField(
                        value = "%.2f".format(priceCents / 100.0),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Đơn giá") },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showPriceDialog) {
                    PriceInputDialog(
                        initialCents = priceCents,
                        onDismiss = { showPriceDialog = false },
                        onConfirm = {
                            priceCents = it
                            showPriceDialog = false
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expandedType = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedType)
                    }
                    DropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        options.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    selectedType = it
                                    expandedType = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Subtotal: %.2f$".format(subtotal))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = item.copy(
                    id = qty,
                    quantity = sl.toIntOrNull() ?: item.quantity,
                    unit = unit,
                    description = description,
                    caseType = caseType,
                    price = priceCents / 100.0,
                    subtotal = (sl.toIntOrNull() ?: 0) * (priceCents / 100.0),
                    type = selectedType
                )
                InventoryStorage.updateItem(context, updated)
                onConfirm(updated)
            }) {
                Text("✔ Cập nhật")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("❌ Hủy")
            }
        }
    )
}
