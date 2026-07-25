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

    // Trust-all SSL factory cho Clover device LAN (dùng self-signed cert)
    private val trustAllFactory: SSLSocketFactory by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        ctx.socketFactory
    }

    private val trustAllHostname = HostnameVerifier { _, _ -> true }

    private fun isLocal(baseUrl: String) = !baseUrl.contains("api.clover.com")

    private fun get(urlStr: String, token: String, baseUrl: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        if (isLocal(baseUrl) && conn is HttpsURLConnection) {
            conn.sslSocketFactory = trustAllFactory
            conn.hostnameVerifier  = trustAllHostname
        }
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout    = 15_000
        return try {
            if (conn.responseCode !in 200..299)
                error("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchOpenOrders(
        baseUrl: String,
        merchantId: String,
        token: String
    ): Result<List<CloverOrder>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/v3/merchants/$merchantId/orders" +
                "?filter=state%3Dopen&expand=lineItems%2ClineItems.item%2CorderType&limit=100"
            gson.fromJson(get(url, token, baseUrl), CloverOrdersResponse::class.java).elements
        }
    }

    suspend fun fetchTables(
        baseUrl: String,
        merchantId: String,
        token: String
    ): Result<List<CloverTable>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/v3/merchants/$merchantId/tables?limit=200"
            gson.fromJson(get(url, token, baseUrl), CloverTablesResponse::class.java).elements
        }
    }
}
