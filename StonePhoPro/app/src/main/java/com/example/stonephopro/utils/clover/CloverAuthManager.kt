package com.example.stonephopro.utils.clover

import android.content.Context
import android.net.Uri
import com.example.stonephopro.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object CloverAuthManager {

    const val REDIRECT_URI = "stonepho://clover/oauth"

    private val APP_ID     get() = BuildConfig.CLOVER_APP_ID
    private val APP_SECRET get() = BuildConfig.CLOVER_APP_SECRET

    // URL mở trình duyệt để merchant đăng nhập Clover và cấp quyền
    fun buildAuthUrl(): String =
        "https://www.clover.com/oauth/authorize" +
        "?client_id=$APP_ID" +
        "&response_type=code" +
        "&redirect_uri=${Uri.encode(REDIRECT_URI)}"

    // Đổi authorization code → access_token (Clover dùng GET, không phải POST)
    suspend fun exchangeCodeForToken(code: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.clover.com/oauth/token" +
                "?client_id=$APP_ID&client_secret=$APP_SECRET&code=$code"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            val body = try {
                if (conn.responseCode !in 200..299) return@runCatching null
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
            Gson().fromJson(body, CloverTokenResponse::class.java)?.accessToken
        }.getOrNull()
    }

    private const val PREFS = "clover_auth"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_MID   = "merchant_id"

    fun saveAuth(context: Context, token: String, merchantId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_MID, merchantId)
            .apply()
        // Cập nhật luôn vào CloverConfig để màn hình order dùng ngay
        CloverConfig.save(context, "https://api.clover.com", merchantId, token)
    }

    fun isAuthenticated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, "").orEmpty().isNotBlank()
    }

    fun getToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "").orEmpty()

    fun getMerchantId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MID, "").orEmpty()

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

private data class CloverTokenResponse(
    @SerializedName("access_token") val accessToken: String?
)
