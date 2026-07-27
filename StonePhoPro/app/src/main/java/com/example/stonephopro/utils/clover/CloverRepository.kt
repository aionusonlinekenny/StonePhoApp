package com.example.stonephopro.utils.clover

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CloverRepository {

    private val gson = Gson()

    // Trust-all SSL cho Clover LAN device (self-signed cert)
    private val trustAllFactory: SSLSocketFactory by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        SSLContext.getInstance("TLS").also { it.init(null, trustAll, SecureRandom()) }.socketFactory
    }
    private val trustAllHostname = HostnameVerifier { _, _ -> true }

    private fun get(urlStr: String, token: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        // Trust-all SSL chỉ dùng cho LAN (không phải clover.com hay stonephovaldosta.com)
        if (conn is HttpsURLConnection && !urlStr.contains("clover.com") && !urlStr.contains("stonephovaldosta.com")) {
            conn.sslSocketFactory = trustAllFactory
            conn.hostnameVerifier  = trustAllHostname
        }
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout    = 15_000
        return try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(urlStr: String, token: String, method: String = "GET"): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout    = 15_000
        return try {
            val code = conn.responseCode
            val body = if (code in 200..299) conn.inputStream.bufferedReader().readText() else ""
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    // ── Chế độ PROXY: gọi qua stonephovaldosta.com/loyalteapp/backend ──────────
    // Token ở đây là auth token của LoyalteApp (không phải Clover token)
    suspend fun fetchOpenOrdersViaProxy(
        proxyToken: String
    ): Result<List<CloverOrder>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${CloverConfig.PROXY_URL}?action=orders"
            val json = get(url, proxyToken)
            gson.fromJson(json, CloverOrdersResponse::class.java).elements.orEmpty()
        }
    }

    suspend fun deleteOrderViaProxy(
        proxyToken: String,
        orderId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${CloverConfig.PROXY_URL}?action=delete_order&order_id=${orderId}"
            val (code, _) = httpGet(url, proxyToken)
            if (code !in 200..299) error("Proxy DELETE HTTP $code")
        }
    }

    suspend fun fetchTablesViaProxy(
        proxyToken: String
    ): Result<List<CloverTable>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${CloverConfig.PROXY_URL}?action=tables"
            gson.fromJson(get(url, proxyToken), CloverTablesResponse::class.java).elements.orEmpty()
        }
    }

    // ── Chế độ DIRECT: gọi thẳng Clover API (cần OAuth token) ────────────────
    suspend fun fetchOpenOrders(
        baseUrl: String,
        merchantId: String,
        token: String
    ): Result<List<CloverOrder>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/v3/merchants/$merchantId/orders" +
                "?filter=state%3Dopen&expand=lineItems%2ClineItems.item%2CorderType&limit=100"
            gson.fromJson(get(url, token), CloverOrdersResponse::class.java).elements
        }
    }

    suspend fun fetchTables(
        baseUrl: String,
        merchantId: String,
        token: String
    ): Result<List<CloverTable>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/v3/merchants/$merchantId/tables?limit=200"
            gson.fromJson(get(url, token), CloverTablesResponse::class.java).elements
        }
    }
}
