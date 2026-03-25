package com.example.stonephopro.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.stonephopro.model.Invoice
import com.example.stonephopro.utils.InvoiceStorage
import com.example.stonephopro.utils.InvoiceStorage.loadInvoicesBetween
import com.example.stonephopro.utils.InvoiceStorage.loadInvoicesInMonth
import com.example.stonephopro.utils.PrinterConfig
import com.example.stonephopro.utils.SocketPrinter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import com.example.stonephopro.components.Button3D
import com.google.gson.Gson
import com.example.stonephopro.utils.BackupUtils
import java.io.File

enum class ViewMode {
    BY_DAY, BY_MONTH, CUSTOM
}

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun InvoiceHistoryScreen(onBack: () -> Unit, permission: String) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("MM-dd-yyyy", Locale.US) }
    val today = formatter.format(Calendar.getInstance().time)
    var lastPrintStatus by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(today) }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var invoices by remember { mutableStateOf(emptyList<Invoice>()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedInvoice by remember { mutableStateOf<Invoice?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.BY_DAY) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    // Export Backup ZIP
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            val backup = BackupUtils.createBackupZip(context)
            if (uri != null && backup != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        backup.inputStream().copyTo(output)
                    }
                    exportMessage = "✅ Đã sao lưu toàn bộ dữ liệu:\n$uri"
                } catch (e: Exception) {
                    exportMessage = "❌ Sao lưu thất bại: ${e.message}"
                }
            }
        }
    )

// Restore ZIP
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val zipFile = File.createTempFile("restore_", ".zip", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        zipFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    val success = BackupUtils.restoreFromZip(context, zipFile)
                    exportMessage = if (success) "✅ Đã khôi phục dữ liệu từ file ZIP" else "❌ Khôi phục thất bại!"
                } catch (e: Exception) {
                    exportMessage = "❌ Lỗi khi khôi phục: ${e.message}"
                }
            }
        }
    )
    var invoiceBeingEdited by remember { mutableStateOf<Invoice?>(null) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    val json = Gson().toJson(invoices)
                    outputStream?.bufferedWriter()?.use { it.write(json) }
                    exportMessage = "✅ Đã xuất file thành công:\n$uri"
                } catch (e: Exception) {
                    exportMessage = "❌ Xuất file thất bại: ${e.message}"
                }
            }
        }
    )

    LaunchedEffect(selectedDate, viewMode, startDate, endDate) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val loaded = when (viewMode) {
                ViewMode.BY_DAY -> InvoiceStorage.loadInvoices(context, selectedDate)
                ViewMode.BY_MONTH -> loadInvoicesInMonth(context, selectedDate)
                ViewMode.CUSTOM -> loadInvoicesBetween(context, startDate, endDate)
            }.sortedWith(compareByDescending<Invoice> { it.date }.thenByDescending { it.time })
            delay(150)
            withContext(Dispatchers.Main) {
                invoices = loaded
                isLoading = false
            }
        }
    }

    if (invoiceBeingEdited != null) {
        EditInvoiceScreen(
            invoice = invoiceBeingEdited!!,
            onSave = { updatedInvoice ->
                InvoiceStorage.updateInvoice(context, updatedInvoice)
                invoiceBeingEdited = null
                // Cập nhật danh sách invoices nếu muốn realtime
                invoices = invoices.map {
                    if (it.id == updatedInvoice.id) updatedInvoice else it
                }
            },
            onCancel = { invoiceBeingEdited = null }
        )
        return
    }


    if (invoiceBeingEdited != null) {
        EditInvoiceScreen(
            invoice = invoiceBeingEdited!!,
            onSave = { updatedInvoice ->
                InvoiceStorage.updateInvoice(context, updatedInvoice)
                invoiceBeingEdited = null
                invoices = invoices.map {
                    if (it.id == updatedInvoice.id) updatedInvoice else it
                }
            },
            onCancel = { invoiceBeingEdited = null }
        )
        return
    }

    if (selectedInvoice != null) {
        InvoiceDetail(
            invoice = selectedInvoice!!,
            onBack = { selectedInvoice = null },
            isAdmin = (permission == "admin"),
            onEdit = { invoice ->
                selectedInvoice = null
                invoiceBeingEdited = invoice
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {


        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth())
            {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            )
            {
                Button3D(
                    text = "⬅ Quay lại",
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp), // ✅ Chiều cao phù hợp
                    gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
                )

                Button3D(
                    text = "📅 By Day",
                    onClick = { viewMode = ViewMode.BY_DAY },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp), // ✅ Chiều cao phù hợp
                    fontSize = 16.sp, // ✅ Chữ to hơn cho dễ đọc
                    gradientColors = if (viewMode == ViewMode.BY_DAY) {
                        listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient xanh dương đậm (Selected)
                    } else {
                        listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)) // ✅ Gradient xám nhạt (Unselected)
                    }
                )

                Button3D(
                    text = "🗓️ By Month",
                    onClick = { viewMode = ViewMode.BY_MONTH },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp), // ✅ Chiều cao phù hợp
                    fontSize = 16.sp, // ✅ Chữ to hơn cho dễ đọc
                    gradientColors = if (viewMode == ViewMode.BY_MONTH) {
                        listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient xanh dương đậm (Selected)
                    } else {
                        listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)) // ✅ Gradient xám nhạt (Unselected)
                    }
                )


                Button3D(
                    text = "📆 Custom Days",
                    onClick = { viewMode = ViewMode.CUSTOM },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp), // ✅ Chiều cao phù hợp
                    fontSize = 16.sp, // ✅ Chữ to hơn cho dễ đọc
                    gradientColors = if (viewMode == ViewMode.CUSTOM) {
                        listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient xanh dương đậm (Selected)
                    } else {
                        listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)) // ✅ Gradient xám nhạt (Unselected)
                    }
                )

                if (permission == "admin") {
                    Button3D(
                        text = "🖨️ In thống kê",
                        onClick = {
                            val summary = buildInvoiceSummaryText(viewMode, invoices)
                            PrinterConfig.getSelectedIpPort()?.let { (ip, port) ->
                                SocketPrinter.printText(ip, port, summary)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp), // ✅ Chiều cao phù hợp
                        fontSize = 16.sp, // ✅ Chữ to hơn cho dễ đọc
                        gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // ✅ Gradient xanh lá đậm
                    )



                    Button3D(
                        text = "💾 Export JSON",
                        onClick = {
                            val fileName = when (viewMode) {
                                ViewMode.BY_DAY -> "invoices_${selectedDate}.json"
                                ViewMode.BY_MONTH -> "invoices_month_${selectedDate}.json"
                                ViewMode.CUSTOM -> "invoices_${startDate}_to_${endDate}.json"
                            }
                            exportLauncher.launch(fileName)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        fontSize = 16.sp,
                        gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient xanh dương đậm
                    )

                    Button3D(
                        text = "📦 Backup Data",
                        onClick = {
                            val dateStr = SimpleDateFormat("MM-dd-yyyy_HH-mm", Locale.US).format(Date())
                            backupLauncher.launch("stonepho_backup_$dateStr.zip")
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        fontSize = 16.sp,
                        gradientColors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)) // ✅ Gradient xanh lá đậm
                    )

                    Button3D(
                        text = "🔁 Restore File",
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/zip"))
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        fontSize = 16.sp,
                        gradientColors = listOf(Color(0xFFFFA726), Color(0xFFF57C00)) // ✅ Gradient cam đậm
                    )

                    Button3D(
                        text = "🗑️ Xoá toàn bộ",
                        onClick = {
                            showDeleteAllConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        fontSize = 16.sp,
                        gradientColors = listOf(Color(0xFFF44336), Color(0xFFD32F2F)) // ✅ Gradient đỏ đậm
                    )

                }

            }
        }
    }

        Spacer(modifier = Modifier.height(12.dp))

        when (viewMode) {
            ViewMode.BY_DAY -> MaterialDatePicker(selectedDate) { selectedDate = it }
            ViewMode.BY_MONTH -> Button3D(
                text = "🗓️ Chọn tháng: $selectedDate",
                onClick = { showMonthPicker = true },
                modifier = Modifier

                    .height(48.dp), // ✅ Chiều cao phù hợp
                fontSize = 16.sp, // ✅ Chữ to hơn cho dễ đọc
                gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient xanh dương
            )
            ViewMode.CUSTOM -> Row {
                MaterialDatePicker(startDate) { startDate = it }
                Spacer(modifier = Modifier.width(8.dp))
                MaterialDatePicker(endDate) { endDate = it }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val totalAmount = invoices.sumOf { it.total }
        val invoiceCount = invoices.size

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🧾 $invoiceCount hóa đơn", fontWeight = FontWeight.Medium)
            val totalLabel = when (viewMode) {
                ViewMode.BY_DAY -> "Total by Day:"
                ViewMode.BY_MONTH -> "Total by Month:"
                ViewMode.CUSTOM -> "Total by Range:"
            }
            Text("$totalLabel %.2f$".format(totalAmount), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (invoices.isEmpty()) {
            Text("Không có hóa đơn nào.")
        } else {
            val itemsPerPage = 15
            val currentPage = remember { mutableStateOf(0) }
            val pagedInvoices = invoices.chunked(itemsPerPage)
            val totalPages = pagedInvoices.size
            val currentInvoices = if (pagedInvoices.isNotEmpty()) pagedInvoices[currentPage.value] else emptyList()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (viewMode == ViewMode.BY_MONTH || viewMode == ViewMode.CUSTOM) {
                    val groupedInvoices = currentInvoices.groupBy { it.date }

                    groupedInvoices.forEach { (date, invoiceList) ->
                        val dailyTotal = invoiceList.sumOf { it.total }
                        Text(
                            text = "📅 Date: $date - Total: %.2f$".format(dailyTotal),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        invoiceList.forEachIndexed { index, invoice ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { selectedInvoice = invoice }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("🧾 ${index + 1}. Order ID: ${invoice.id}", fontWeight = FontWeight.SemiBold)
                                            Text("💵 Total: %.2f$".format(invoice.total), fontSize = 13.sp)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("🕒 ${invoice.time}", fontSize = 13.sp, color = Color.Gray)
                                            Text(
                                                "❌ Xoá",
                                                color = if (permission == "admin") Color.Red else Color.LightGray,
                                                modifier = Modifier.clickable(enabled = permission == "admin") {
                                                    invoiceToDelete = invoice
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    currentInvoices.forEachIndexed { index, invoice ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { selectedInvoice = invoice }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("🧾 ${index + 1}. Order ID: ${invoice.id}", fontWeight = FontWeight.SemiBold)
                                        Text("💵 Total: %.2f$".format(invoice.total), fontSize = 13.sp)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("🕒 ${invoice.time}", fontSize = 13.sp, color = Color.Gray)

                                        Row {
                                            // ✅ Nút "❌ Xoá"
                                            Text(
                                                "❌ Xoá",
                                                color = if (permission == "admin") Color.Red else Color.LightGray,
                                                modifier = Modifier.clickable(enabled = permission == "admin") {
                                                    invoiceToDelete = invoice
                                                }
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // ✅ Nút "🔄 Reprint"
                                            Text(
                                                "🔄 Reprint",
                                                color = Color.Blue,
                                                modifier = Modifier.clickable {
                                                    scope.launch {
                                                        val now = Calendar.getInstance()
                                                        val dateStr = SimpleDateFormat("MM-dd-yyyy", Locale.US).format(now.time)
                                                        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(now.time)

                                                        val productMap = invoice.items.groupBy { it } // Chuyển List<Product> thành Map<Product, Int>
                                                            .mapValues { it.value.size }

                                                        val invoiceText = buildPrintableReceipt(
                                                            invoice.id,
                                                            productMap, // ✅ Truyền đúng kiểu Map<Product, Int>
                                                            invoice.date,
                                                            invoice.time,
                                                            invoice.subtotal,
                                                            invoice.discount,
                                                            invoice.tax,
                                                            invoice.total
                                                        )

                                                        val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                                                        try {
                                                            SocketPrinter.printText(ip, port, invoiceText)
                                                            lastPrintStatus = "✅ Reprinted Successfully: ${invoice.id}"
                                                        } catch (e: Exception) {
                                                            lastPrintStatus = "❌ Reprint Error: ${e.message}"
                                                        } finally {
                                                            showDialog = true
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }
                }


                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(totalPages) { page ->
                            Text(
                                text = "${page + 1}",
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable { currentPage.value = page },
                                color = if (page == currentPage.value) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontWeight = if (page == currentPage.value) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            onMonthSelected = { year, monthIndex ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, monthIndex)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val formatter = SimpleDateFormat("MM-dd-yyyy", Locale.US)
                selectedDate = formatter.format(cal.time)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false }
        )
    }

    if (invoiceToDelete != null) {
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = { Text("Xác nhận xoá") },
            text = { Text("Bạn có chắc muốn xoá hoá đơn này không?\nID: ${invoiceToDelete?.id}") },
            confirmButton = {
                TextButton(onClick = {
                    invoiceToDelete?.let {
                        InvoiceStorage.deleteInvoice(context, it.date, it.id)
                        invoices = invoices.filterNot { inv -> inv.id == it.id }
                    }
                    invoiceToDelete = null
                }) {
                    Text("✔ Xoá")
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToDelete = null }) {
                    Text("❌ Hủy")
                }
            }
        )
    }
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Xác nhận xoá toàn bộ") },
            text = { Text("Bạn có chắc muốn xoá toàn bộ hoá đơn không?\nHành động này không thể hoàn tác!") },
            confirmButton = {
                TextButton(onClick = {
                    val folder = File(context.filesDir, "invoices")
                    folder.listFiles()?.forEach { it.delete() }
                    invoices = emptyList()
                    showDeleteAllConfirm = false
                    exportMessage = "✅ Đã xoá toàn bộ hóa đơn"
                }) {
                    Text("✔ Xoá")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("❌ Hủy")
                }
            }
        )
    }

    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Thông báo xuất file") },
            text = { Text(exportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDatePicker(
    selectedDate: String,
    onDateSelected: (String) -> Unit
) {
    val openDialog = remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val formatter = remember {
        SimpleDateFormat("MM-dd-yyyy", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    if (openDialog.value) {
        DatePickerDialog(
            onDismissRequest = { openDialog.value = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        calendar.timeInMillis = millis
                        calendar.set(Calendar.HOUR_OF_DAY, 12)
                        val dateString = formatter.format(calendar.time)
                        onDateSelected(dateString)
                    }
                    openDialog.value = false
                }) {
                    Text("Chọn")
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog.value = false }) {
                    Text("Hủy")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Button3D(
        text = "📅 Ngày: $selectedDate",
        onClick = { openDialog.value = true },
        modifier = Modifier
        .height(48.dp),
        gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // ✅ Gradient xanh dương
    )
}

fun buildInvoiceSummaryText(viewMode: ViewMode, invoices: List<Invoice>): String {
    val sb = StringBuilder()
    sb.appendLine("         STONE PHO - Invoice Summary")
    sb.appendLine("==========================================")

    when (viewMode) {
        ViewMode.BY_DAY -> {
            invoices.forEach {
                sb.appendLine("Invoice ID: ${it.id.take(10)}     Total: ${"%.2f".format(it.total)}$")
            }
        }

        ViewMode.BY_MONTH, ViewMode.CUSTOM -> {
            val grouped = invoices.groupBy { it.date }
            grouped.forEach { (date, list) ->
                sb.appendLine("Date: $date")
                list.forEach {
                    sb.appendLine("Invoice ID: ${it.id.take(10)}    Time: ${it.time}    ${"%.2f".format(it.total)}$")
                }
                sb.appendLine("Subtotal: ${"%.2f".format(list.sumOf { it.total })}$")
                sb.appendLine("-----------------------------------")
            }

            val grandTotal = invoices.sumOf { it.total }
            sb.appendLine("GRAND TOTAL: ${"%.2f".format(grandTotal)}$")
        }
    }

    sb.appendLine("==========================================")
    sb.appendLine(" Thank you !!!")
    sb.appendLine("\n\n\n")
    return sb.toString()
}

