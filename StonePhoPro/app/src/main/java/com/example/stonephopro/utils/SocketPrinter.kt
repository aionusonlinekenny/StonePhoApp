package com.example.stonephopro.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object SocketPrinter {

    // suspend — caller waits for actual socket result; exceptions propagate to caller
    suspend fun printText(ip: String, port: Int = 9100, content: String) {
        withContext(Dispatchers.IO) {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 3000)
            try {
                val out = socket.getOutputStream()
                out.write(byteArrayOf(0x1b, 0x40))           // ESC @ reset
                val bytes = content.toByteArray(Charsets.US_ASCII)
                out.write(bytes)
                out.write("\n\n\n".toByteArray())
                out.write(byteArrayOf(0x1d, 0x56, 0x00))     // GS V 0  full cut
                out.flush()
                Log.d("SocketPrinter", "✅ $ip:$port — ${bytes.size} bytes sent")
            } finally {
                socket.close()
            }
        }
    }
}
