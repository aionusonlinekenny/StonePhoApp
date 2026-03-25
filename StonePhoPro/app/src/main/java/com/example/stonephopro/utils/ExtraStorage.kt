package com.example.stonephopro.utils

import android.content.Context
import com.example.stonephopro.model.ExtraOption
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ExtraStorage {
    private val gson = Gson()

    fun saveExtras(context: Context, listName: String, extras: List<ExtraOption>) {
        val folder = File(context.filesDir, "extras")
        if (!folder.exists()) folder.mkdirs()

        val file = File(folder, "$listName.json")
        val json = gson.toJson(extras)
        file.writeText(json)
    }

    fun loadExtras(context: Context, listName: String): List<ExtraOption> {
        val folder = File(context.filesDir, "extras")
        val file = File(folder, "$listName.json")
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val type = object : TypeToken<List<ExtraOption>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
