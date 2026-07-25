package com.example.stonephopro.utils.clover

import android.content.Context

object CloverConfig {
    private const val PREFS        = "clover_config"
    private const val KEY_MODE     = "mode"          // "proxy" | "direct"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MERCHANT_ID  = "merchant_id"
    private const val KEY_ACCESS_TOKEN = "access_token"

    // URL backend proxy (stonephovaldosta.com) — không cần OAuth
    const val PROXY_URL = "https://www.stonephovaldosta.com/loyalteapp/backend"

    // Secret key gửi lên proxy — phải khớp với clover.php trên server
    const val PROXY_SECRET = "StonePhoClover@2024"

    // URL Clover trực tiếp — cần OAuth token
    const val CLOVER_DIRECT_URL = "https://api.clover.com"

    enum class Mode { PROXY, DIRECT }

    fun saveProxy(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, "proxy")
            .putString(KEY_BASE_URL, PROXY_URL)
            .putString(KEY_ACCESS_TOKEN, PROXY_SECRET)
            .putString(KEY_MERCHANT_ID, "")
            .apply()
    }

    fun saveDirect(context: Context, merchantId: String, accessToken: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, "direct")
            .putString(KEY_BASE_URL, CLOVER_DIRECT_URL)
            .putString(KEY_MERCHANT_ID, merchantId.trim())
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .apply()
    }

    // Dùng cho OAuth callback (giữ tương thích với CloverAuthManager)
    fun save(context: Context, baseUrl: String, merchantId: String, accessToken: String) {
        val mode = if (baseUrl.contains("stonephovaldosta")) "proxy" else "direct"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode)
            .putString(KEY_BASE_URL, baseUrl.trimEnd('/'))
            .putString(KEY_MERCHANT_ID, merchantId.trim())
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .apply()
    }

    fun getManualTableCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("manual_table_count", 0)

    fun setManualTableCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("manual_table_count", count)
            .apply()
    }

    fun getMode(context: Context): Mode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, "direct")
        return if (raw == "proxy") Mode.PROXY else Mode.DIRECT
    }

    fun load(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(KEY_BASE_URL, CLOVER_DIRECT_URL) ?: CLOVER_DIRECT_URL,
            prefs.getString(KEY_MERCHANT_ID, "") ?: "",
            prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        )
    }
}
