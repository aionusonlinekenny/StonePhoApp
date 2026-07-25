package com.example.stonephopro.utils.clover

import android.content.Context

object CloverConfig {
    private const val PREFS = "clover_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MERCHANT_ID = "merchant_id"
    private const val KEY_ACCESS_TOKEN = "access_token"

    fun save(context: Context, baseUrl: String, merchantId: String, accessToken: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BASE_URL, baseUrl.trimEnd('/'))
            .putString(KEY_MERCHANT_ID, merchantId.trim())
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .apply()
    }

    fun load(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(KEY_BASE_URL, "https://api.clover.com") ?: "https://api.clover.com",
            prefs.getString(KEY_MERCHANT_ID, "") ?: "",
            prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        )
    }
}
