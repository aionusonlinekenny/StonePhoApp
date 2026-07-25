package com.example.stonephopro.utils.clover

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CloverRepository {

    private val gson = Gson()

    // Client tin tưởng mọi SSL cert — cần thiết cho Clover device trên LAN (self-signed cert)
    private val localClient: OkHttpClient by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private val cloudClient: OkHttpClient by lazy { OkHttpClient() }

    private fun clientFor(baseUrl: String) =
        if (baseUrl.contains("api.clover.com")) cloudClient else localClient

    suspend fun fetchOpenOrders(
        baseUrl: String,
        merchantId: String,
        token: String
    ): Result<List<CloverOrder>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/v3/merchants/$merchantId/orders" +
                "?filter=state%3Dopen&expand=lineItems%2ClineItems.item%2CorderType&limit=100"
            val response = get(url, token, baseUrl)
            val parsed = gson.fromJson(response, CloverOrdersResponse::class.java)
            Result.success(parsed.elements)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Lấy danh sách bàn từ Clover table service
    suspend fun fetchTables(
        baseUrl: String,
        merchantId: String,
        token: String
    ): Result<List<CloverTable>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/v3/merchants/$merchantId/tables?limit=200"
            val response = get(url, token, baseUrl)
            val parsed = gson.fromJson(response, CloverTablesResponse::class.java)
            Result.success(parsed.elements)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun get(url: String, token: String, baseUrl: String): String {
        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()
        clientFor(baseUrl).newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}: ${response.message}")
            return response.body?.string() ?: error("Empty response body")
        }
    }
}
