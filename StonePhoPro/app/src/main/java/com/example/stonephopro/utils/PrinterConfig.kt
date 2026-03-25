package com.example.stonephopro.utils

import android.content.Context
import java.io.File

object PrinterConfig {
    var selectedPrinterAddress: String? = null
        private set

    private const val FILE_NAME = "printer_config.txt"

    fun saveSelectedPrinter(context: Context, address: String) {
        selectedPrinterAddress = address
        File(context.filesDir, FILE_NAME).writeText(address)
    }

    fun loadSavedPrinter(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            selectedPrinterAddress = file.readText().trim()
        }
    }

    fun getSelectedIpPort(): Pair<String, Int>? {
        return selectedPrinterAddress?.removePrefix("TCP:")?.split(":")?.let {
            if (it.size == 2) it[0] to it[1].toInt() else null
        }
    }
}
