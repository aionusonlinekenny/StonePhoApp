package com.example.stonephopro.utils.clover

import android.content.Context
import android.net.Uri
import com.example.stonephopro.BuildConfig

object CloverAuthManager {

    // URL này phải khớp với Site URL đã nhập trên Clover Developer Dashboard
    const val REDIRECT_URI = "https://stonepho.app/clover/callback"

    private val APP_ID get() = BuildConfig.CLOVER_APP_ID

    // URL mở trong WebView để merchant đăng nhập Clover
    // Dùng "Token (Testing Only)" trên dashboard → Clover trả token thẳng trong redirect URL
    fun buildAuthUrl(): String =
        "https://www.clover.com/oauth/authorize" +
        "?client_id=$APP_ID" +
        "&response_type=token" +
        "&redirect_uri=${Uri.encode(REDIRECT_URI)}"

    // Token có thể nằm trong query param HOẶC URL fragment (#)
    // Dạng query:    https://stonepho.app/clover/callback?merchant_id=XXX&token=YYY
    // Dạng fragment: https://stonepho.app/clover/callback#access_token=YYY&merchant_id=XXX
    fun parseTokenFromRedirect(url: String): Pair<String, String>? {
        if (!url.startsWith(REDIRECT_URI)) return null
        val uri = Uri.parse(url)

        // Thử query params
        var token      = uri.getQueryParameter("token") ?: uri.getQueryParameter("access_token")
        var merchantId = uri.getQueryParameter("merchant_id") ?: ""

        // Thử URL fragment nếu chưa tìm thấy token
        val fragment = uri.fragment
        if (token == null && !fragment.isNullOrBlank()) {
            val fragUri = Uri.parse("?$fragment")
            token      = fragUri.getQueryParameter("token") ?: fragUri.getQueryParameter("access_token")
            if (merchantId.isEmpty())
                merchantId = fragUri.getQueryParameter("merchant_id") ?: ""
        }

        return if (token != null) Pair(token, merchantId) else null
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
