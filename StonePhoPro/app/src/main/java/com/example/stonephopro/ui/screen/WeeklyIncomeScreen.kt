package com.example.stonephopro.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stonephopro.components.Button3D
import com.example.stonephopro.utils.InvoiceStorage
import com.example.stonephopro.utils.PrinterConfig
import com.example.stonephopro.utils.SocketPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ── SharedPreferences helpers ────────────────────────────────────────────────
private const val WEEKLY_PREFS    = "weekly_income"
private const val OVERRIDE_PREFIX = "override_"

private fun loadOverride(ctx: Context, date: String): Double? {
    val v = ctx.getSharedPreferences(WEEKLY_PREFS, Context.MODE_PRIVATE)
        .getFloat("$OVERRIDE_PREFIX$date", -1f)
    return if (v < 0f) null else v.toDouble()
}

private fun saveOverride(ctx: Context, date: String, value: Double) {
    ctx.getSharedPreferences(WEEKLY_PREFS, Context.MODE_PRIVATE)
        .edit().putFloat("$OVERRIDE_PREFIX$date", value.toFloat()).apply()
}

private fun clearOverride(ctx: Context, date: String) {
    ctx.getSharedPreferences(WEEKLY_PREFS, Context.MODE_PRIVATE)
        .edit().remove("$OVERRIDE_PREFIX$date").apply()
}

private data class DaySummary(
    val date: String,
    val label: String,
    val autoTotal: Double,
    val override: Double?
) {
    val effective: Double get() = override ?: autoTotal
}

// ── Tính 7 ngày của tuần (weekOffset=0 = tuần hiện tại, -1 = tuần trước) ────
private fun buildWeekDates(weekOffset: Int): List<Pair<String, String>> {
    val fmtKey = SimpleDateFormat("MM-dd-yyyy", Locale.US)
    val fmtDay = SimpleDateFormat("EEE MM/dd", Locale.US)
    val cal    = Calendar.getInstance()
    // Về thứ Hai của tuần hiện tại
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    // Dịch theo offset tuần
    cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
    return List(7) {
        val key   = fmtKey.format(cal.time)
        val label = fmtDay.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        key to label
    }
}

private fun weekRangeLabel(weekOffset: Int): String {
    val fmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    val cal = Calendar.getInstance()
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
    val start = fmt.format(cal.time)
    cal.add(Calendar.DAY_OF_MONTH, 6)
    val end = fmt.format(cal.time)
    return "$start – $end"
}

// ── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun WeeklyIncomeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var weekOffset   by remember { mutableStateOf(0) }
    var days         by remember { mutableStateOf<List<DaySummary>>(emptyList()) }
    var editingDate  by remember { mutableStateOf<String?>(null) }
    var editValue    by remember { mutableStateOf("") }
    var printResult  by remember { mutableStateOf("") }
    var showPrint    by remember { mutableStateOf(false) }
    var isLoading    by remember { mutableStateOf(false) }

    val today = InvoiceStorage.getTodayDate()

    // Reload khi đổi tuần
    LaunchedEffect(weekOffset) {
        isLoading = true
        editingDate = null
        val weekDates = buildWeekDates(weekOffset)
        val loaded = withContext(Dispatchers.IO) {
            weekDates.map { (date, label) ->
                val auto = InvoiceStorage.loadInvoices(context, date).sumOf { it.total }
                DaySummary(
                    date      = date,
                    label     = label,
                    autoTotal = auto,
                    override  = loadOverride(context, date)
                )
            }
        }
        days      = loaded
        isLoading = false
    }

    val weekTotal = days.sumOf { it.effective }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {

        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A237E))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("📊", fontSize = 20.sp)
                Column {
                    Text(
                        "Tổng Thu Nhập Tuần",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    Text(
                        weekRangeLabel(weekOffset),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp
                    )
                }
            }
            Button3D(
                text = "✕ Đóng",
                onClick = onBack,
                gradientColors = listOf(Color(0xFF546E7A), Color(0xFF263238)),
                fontSize = 13.sp
            )
        }

        // ── Điều hướng tuần ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF283593))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { weekOffset-- }) {
                Text("◀ Tuần trước", color = Color.White, fontSize = 13.sp)
            }
            Text(
                if (weekOffset == 0) "Tuần này" else if (weekOffset == -1) "Tuần trước" else "${-weekOffset} tuần trước",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
            )
            TextButton(
                onClick = { weekOffset++ },
                enabled = weekOffset < 0
            ) {
                Text(
                    "Tuần sau ▶",
                    color = if (weekOffset < 0) Color.White else Color.White.copy(alpha = 0.3f),
                    fontSize = 13.sp
                )
            }
        }

        // ── Banner tổng tuần ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1565C0))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💵 TỔNG TUẦN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    "$%,.2f".format(weekTotal),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp
                )
            }
        }

        // ── Header cột ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE8EAF6))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Ngày",        modifier = Modifier.weight(1.3f), fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Text("Hóa đơn",    modifier = Modifier.weight(1f),   fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
            Text("Điều chỉnh", modifier = Modifier.weight(1.2f), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Text("Thực tế",    modifier = Modifier.weight(1f),   fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        // ── Danh sách ngày ────────────────────────────────────────────────
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(days, key = { it.date }) { day ->
                val isToday     = day.date == today
                val hasOverride = day.override != null
                val isEditing   = editingDate == day.date

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isToday) Color(0xFFFFF9C4) else Color.White)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ngày
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text(
                            day.label,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        if (isToday) Text("hôm nay", fontSize = 10.sp, color = Color(0xFFE65100))
                    }

                    // Tổng hóa đơn (auto)
                    Text(
                        if (day.autoTotal > 0) "$%,.2f".format(day.autoTotal) else "—",
                        modifier   = Modifier.weight(1f),
                        fontSize   = 13.sp,
                        textAlign  = TextAlign.End,
                        color      = if (hasOverride) Color(0xFFBDBDBD) else Color(0xFF1565C0)
                    )

                    // Điều chỉnh
                    Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                        if (isEditing) {
                            OutlinedTextField(
                                value         = editValue,
                                onValueChange = { v ->
                                    if (v.isEmpty() || v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$")))
                                        editValue = v
                                },
                                singleLine    = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier      = Modifier.width(100.dp),
                                textStyle     = LocalTextStyle.current.copy(fontSize = 13.sp, textAlign = TextAlign.End)
                            )
                        } else {
                            TextButton(onClick = {
                                editingDate = day.date
                                editValue   = "%.2f".format(day.override ?: day.autoTotal)
                            }) {
                                Text(
                                    if (hasOverride) "$%,.2f".format(day.override!!) else "✏️ Sửa",
                                    fontSize = if (hasOverride) 12.sp else 11.sp,
                                    color    = if (hasOverride) Color(0xFFE65100) else Color.Gray
                                )
                            }
                        }
                    }

                    // Thực tế
                    Text(
                        if (day.effective > 0) "$%,.2f".format(day.effective) else "—",
                        modifier     = Modifier.weight(1f),
                        fontSize     = 14.sp,
                        fontWeight   = FontWeight.Bold,
                        textAlign    = TextAlign.End,
                        color        = if (day.effective > 0) Color(0xFF2E7D32) else Color.LightGray
                    )
                }
                HorizontalDivider(color = Color(0xFFEEEEEE))
            }
        }

        // ── Buttons ───────────────────────────────────────────────────────
        if (editingDate != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button3D(
                    text = "✕ Hủy",
                    onClick = { editingDate = null; editValue = "" },
                    gradientColors = listOf(Color(0xFF78909C), Color(0xFF546E7A)),
                    modifier = Modifier.weight(1f).height(44.dp), fontSize = 13.sp
                )
                Button3D(
                    text = "🗑 Xóa chỉnh",
                    onClick = {
                        val d = editingDate!!
                        clearOverride(context, d)
                        days = days.map { if (it.date == d) it.copy(override = null) else it }
                        editingDate = null; editValue = ""
                    },
                    gradientColors = listOf(Color(0xFFEF5350), Color(0xFFC62828)),
                    modifier = Modifier.weight(1.4f).height(44.dp), fontSize = 12.sp
                )
                Button3D(
                    text = "✔ Lưu",
                    onClick = {
                        val d = editingDate!!
                        val v = editValue.toDoubleOrNull()
                        if (v != null && v >= 0) {
                            saveOverride(context, d, v)
                            days = days.map { if (it.date == d) it.copy(override = v) else it }
                        }
                        editingDate = null; editValue = ""
                    },
                    gradientColors = listOf(Color(0xFF43A047), Color(0xFF1B5E20)),
                    modifier = Modifier.weight(1f).height(44.dp), fontSize = 13.sp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp)
            ) {
                Button3D(
                    text = "🖨️ In tổng tuần",
                    onClick = {
                        scope.launch {
                            val sb = StringBuilder()
                            sb.appendLine("        STONE PHO — WEEKLY CASH INCOME")
                            sb.appendLine("  ${weekRangeLabel(weekOffset)}")
                            sb.appendLine("------------------------------------------------")
                            days.forEach { d ->
                                val lbl  = d.label.padEnd(14)
                                val amt  = "$%,.2f".format(d.effective)
                                val mark = if (d.override != null) "*" else " "
                                sb.appendLine("$mark $lbl $amt")
                            }
                            sb.appendLine("------------------------------------------------")
                            sb.appendLine("TOTAL:           ${"$%,.2f".format(weekTotal)}")
                            sb.appendLine("* = số điều chỉnh thủ công")
                            sb.appendLine("\n\n\n")

                            val (ip, port) = PrinterConfig.getSelectedIpPort() ?: ("192.168.0.114" to 9100)
                            printResult = try {
                                SocketPrinter.printText(ip, port, sb.toString())
                                "✅ Đã in tổng tuần"
                            } catch (e: Exception) {
                                "❌ Lỗi máy in: ${e.message}"
                            }
                            showPrint = true
                        }
                    },
                    gradientColors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1)),
                    modifier = Modifier.fillMaxWidth().height(48.dp), fontSize = 14.sp
                )
            }
        }
    }

    if (showPrint) {
        AlertDialog(
            onDismissRequest = { showPrint = false },
            confirmButton    = { TextButton(onClick = { showPrint = false }) { Text("OK") } },
            title = { Text("In tổng tuần") },
            text  = { Text(printResult) }
        )
    }
}
