package com.example.stonephopro.utils.print

import android.content.Context

object KitchenPrinterConfig {
    private const val PREFS = "kitchen_printers"

    enum class Zone(val label: String, val emoji: String) {
        FOOD("FOOD", "🍜"),
        DRINK("DRINK", "🥤"),
        BANHMY("BanhMy", "🥖")
    }

    fun getIpPort(ctx: Context, zone: Zone): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("ip_${zone.name}", "") ?: ""

    fun setIpPort(ctx: Context, zone: Zone, ipPort: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ip_${zone.name}", ipPort.trim()).apply()
    }

    fun getCategoryZone(ctx: Context, category: String): Zone {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("cat_$category", Zone.FOOD.name) ?: Zone.FOOD.name
        return Zone.entries.find { it.name == raw } ?: Zone.FOOD
    }

    fun setCategoryZone(ctx: Context, category: String, zone: Zone) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("cat_$category", zone.name).apply()
    }

    fun parseIpPort(ipPort: String): Pair<String, Int>? {
        val cleaned = ipPort.removePrefix("TCP:")
        val parts = cleaned.split(":")
        return if (parts.size >= 2) Pair(parts[0], parts[1].toIntOrNull() ?: 9100) else null
    }
}
