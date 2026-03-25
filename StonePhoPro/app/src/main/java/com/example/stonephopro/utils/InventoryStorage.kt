package com.example.stonephopro.utils

import android.content.Context
import android.util.Log
import com.example.stonephopro.model.InventoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object InventoryStorage {
    private const val FOLDER_NAME = "inventory"
    private val gson = Gson()

    fun getTodayDate(): String {
        val formatter = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        return formatter.format(Date())
    }

    fun saveItem(context: Context, item: InventoryItem) {
        val folder = File(context.filesDir, FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()

        val file = File(folder, "${item.date}.json")
        val itemList = loadItems(context, item.date).toMutableList()
        itemList.add(item)

        file.writeText(gson.toJson(itemList))
        Log.d("InventoryStorage", "✅ Saved item ${item.id} on ${item.date}")
    }

    fun loadItems(context: Context, date: String): List<InventoryItem> {
        val folder = File(context.filesDir, FOLDER_NAME)
        val file = File(folder, "$date.json")
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText()
            val type = object : TypeToken<List<InventoryItem>>() {}.type
            gson.fromJson(content, type)
        } catch (e: Exception) {
            Log.e("InventoryStorage", "❌ Failed to load items: ${e.message}")
            emptyList()
        }
    }

    fun loadAllItems(context: Context): List<InventoryItem> {
        val folder = File(context.filesDir, "inventory")
        if (!folder.exists()) return emptyList()

        val allItems = mutableListOf<InventoryItem>()
        val formatter = SimpleDateFormat("MM-dd-yyyy", Locale.US)

        folder.listFiles()?.forEach { file ->
            try {
                // Chỉ đọc những file tên đúng định dạng ngày
                formatter.parse(file.nameWithoutExtension)  // kiểm tra định dạng
                val content = file.readText()
                val type = object : TypeToken<List<InventoryItem>>() {}.type
                val items = gson.fromJson<List<InventoryItem>>(content, type)
                allItems.addAll(items)
            } catch (_: Exception) {
                // Bỏ qua file không đúng định dạng ngày
            }
        }

        return allItems
    }


    fun deleteItem(context: Context, date: String, itemId: String) {
        val folder = File(context.filesDir, FOLDER_NAME)
        val file = File(folder, "$date.json")
        if (!file.exists()) return

        val itemList = loadItems(context, date).toMutableList()
        val removed = itemList.removeAll { it.id == itemId }
        if (removed) {
            file.writeText(gson.toJson(itemList))
            Log.d("InventoryStorage", "🗑️ Deleted item $itemId from $date.json")
        }
    }

    fun updateItem(context: Context, updatedItem: InventoryItem) {
        val folder = File(context.filesDir, FOLDER_NAME)
        val file = File(folder, "${updatedItem.date}.json")
        if (!file.exists()) return

        val itemList = loadItems(context, updatedItem.date).toMutableList()
        val index = itemList.indexOfFirst {
            it.id == updatedItem.id && it.type == updatedItem.type && it.date == updatedItem.date
        }

        if (index != -1) {
            itemList[index] = updatedItem
            file.writeText(gson.toJson(itemList))
            Log.d("InventoryStorage", "✏️ Updated item ${updatedItem.id} on ${updatedItem.date}")
        }
    }

}
