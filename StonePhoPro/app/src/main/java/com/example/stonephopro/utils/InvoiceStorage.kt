// InvoiceStorage.kt
package com.example.stonephopro.utils

import android.content.Context
import android.util.Log
import com.example.stonephopro.model.Invoice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object InvoiceStorage {

    private const val INVOICE_FOLDER = "invoices"
    private val gson = Gson()

    fun saveInvoice(context: Context, invoice: Invoice) {
        val folder = File(context.filesDir, INVOICE_FOLDER)
        if (!folder.exists()) folder.mkdirs()

        val date = invoice.date
        val file = File(folder, "$date.json")

        val invoices = loadInvoices(context, date).toMutableList()
        invoices.add(invoice)

        file.writeText(gson.toJson(invoices))
        Log.d("InvoiceStorage", "Đã lưu hóa đơn ${invoice.id} vào file: ${file.absolutePath}")
    }
    fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.US)
        return formatter.format(Date())
    }

    fun deleteInvoice(context: Context, date: String, invoiceId: String) {
        val folder = File(context.filesDir, INVOICE_FOLDER)
        val file = File(folder, "$date.json")
        if (!file.exists()) return

        val invoices = loadInvoices(context, date).toMutableList()
        val removed = invoices.removeAll { it.id == invoiceId }
        if (removed) {
            file.writeText(gson.toJson(invoices))
            Log.d("InvoiceStorage", "Đã xoá hoá đơn $invoiceId khỏi $date.json")
        }
    }
    fun updateInvoice(context: Context, updated: Invoice) {
        val folder = File(context.filesDir, "invoices")
        val file = File(folder, "${updated.date}.json")
        if (!file.exists()) return

        val invoices = loadInvoices(context, updated.date).toMutableList()
        val index = invoices.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            invoices[index] = updated
            file.writeText(gson.toJson(invoices))
        }
    }

    fun loadInvoices(context: Context, date: String): List<Invoice> {
        val folder = File(context.filesDir, INVOICE_FOLDER)
        val file = File(folder, "$date.json")
        if (!file.exists()) {
            Log.d("InvoiceStorage", "Không tìm thấy file hóa đơn cho ngày: $date")
            return emptyList()
        }

        val type = object : TypeToken<List<Invoice>>() {}.type
        val content = file.readText()
        //Log.d("InvoiceStorage", "Đọc file: ${file.absolutePath}, nội dung: $content")
        return gson.fromJson(content, type)
    }

    fun generateInvoiceId(): String {
        return UUID.randomUUID().toString().substring(0, 8)
    }

    fun getTodayDate(): String {
        val formatter = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        return formatter.format(Date())
    }
    fun loadInvoicesInMonth(context: Context, selectedDate: String): List<Invoice> {
        val folder = File(context.filesDir, "invoices")
        if (!folder.exists()) return emptyList()

        val formatter = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        val target = formatter.parse(selectedDate)
        val calendar = Calendar.getInstance()
        calendar.time = target!!
        val targetMonth = calendar.get(Calendar.MONTH)
        val targetYear = calendar.get(Calendar.YEAR)

        val invoices = mutableListOf<Invoice>()
        folder.listFiles()?.forEach { file ->
            try {
                val date = formatter.parse(file.nameWithoutExtension)
                val fileCalendar = Calendar.getInstance().apply { time = date!! }
                if (
                    fileCalendar.get(Calendar.MONTH) == targetMonth &&
                    fileCalendar.get(Calendar.YEAR) == targetYear
                ) {
                    invoices.addAll(loadInvoices(context, file.nameWithoutExtension))
                }
            } catch (_: Exception) {}
        }
        return invoices
    }
    fun exportInvoicesToExternalStorage(context: Context, invoices: List<Invoice>, fileName: String): Boolean {
        return try {
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val exportFile = File(exportDir, "$fileName.json")
            val json = gson.toJson(invoices)

            exportFile.writeText(json)
            Log.d("InvoiceStorage", "✅ Exported to: ${exportFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("InvoiceStorage", "❌ Export error: ${e.message}")
            false
        }
    }

    fun loadInvoicesBetween(context: Context, start: String, end: String): List<Invoice> {
        val folder = File(context.filesDir, "invoices")
        if (!folder.exists()) return emptyList()

        val formatter = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        val startDate = formatter.parse(start)
        val endDate = formatter.parse(end)

        val invoices = mutableListOf<Invoice>()
        folder.listFiles()?.forEach { file ->
            try {
                val date = formatter.parse(file.nameWithoutExtension)
                if (date != null && !date.before(startDate) && !date.after(endDate)) {
                    invoices.addAll(loadInvoices(context, file.nameWithoutExtension))
                }
            } catch (_: Exception) {}
        }
        return invoices
    }


}
