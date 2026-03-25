package com.example.stonephopro.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

object SocketPrinter {

    fun printText(ip: String, port: Int = 9100, content: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 3000)

                val outputStream: OutputStream = socket.getOutputStream()
                outputStream.write(byteArrayOf(0x1b, 0x40)) // reset printer
                outputStream.write(content.toByteArray(Charsets.US_ASCII))
                outputStream.write("\n\n\n".toByteArray())
                outputStream.write(byteArrayOf(0x1d, 0x56, 0x00)) // cut
                outputStream.flush()

                socket.close()
                Log.d("SocketPrinter", "✅ In thành công đến $ip:$port")
            } catch (e: Exception) {
                Log.e("SocketPrinter", "❌ In lỗi: ${e.message}")
            }
        }
    }
}
