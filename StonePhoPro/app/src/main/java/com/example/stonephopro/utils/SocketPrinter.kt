package com.example.stonephopro.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

object SocketPrinter {

    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000

    suspend fun printText(ip: String, port: Int = 9100, content: String) {
        withContext(Dispatchers.IO) {
            try {
                sendToPrinter(ip, port, content)
            } catch (e: Exception) {
                Log.w("SocketPrinter", "⚠️ Lần 1 thất bại, thử lại... (${e.message})")
                sendToPrinter(ip, port, content) // retry 1 lần
            }
        }
    }

    private fun sendToPrinter(ip: String, port: Int, content: String) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS

            val outputStream: OutputStream = socket.getOutputStream()
            outputStream.write(byteArrayOf(0x1b, 0x40)) // ESC @ reset printer
            outputStream.write(content.toByteArray(Charsets.US_ASCII))
            outputStream.write("\n\n\n".toByteArray())
            outputStream.write(byteArrayOf(0x1d, 0x56, 0x00)) // GS V cut
            outputStream.flush()

            Log.d("SocketPrinter", "✅ In thành công đến $ip:$port")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}

