package com.example.stonephopro.utils.print

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

object PrinterHelper {
    suspend fun printReceipt(context: Context, ip: String, port: Int = 9100, text: String) {
        try {
            withContext(Dispatchers.IO) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 5000)
                    val outputStream = socket.getOutputStream()
                    val writer = OutputStreamWriter(outputStream, Charsets.US_ASCII)

                    writer.write("\u001B@") // ESC @ init
                    writer.write(text)
                    writer.write("\n\n\n\n")
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
