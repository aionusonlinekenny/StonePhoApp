package com.example.stonephopro.utils

import android.content.Context
import com.example.stonephopro.model.Category
import com.example.stonephopro.model.Product
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object MenuStorage {
    private val gson = Gson()

    fun saveMenu(context: Context, categories: List<Category>, products: List<Product>) {
        val catFile = File(context.filesDir, "catalog_data.json")
        val productFile = File(context.filesDir, "product_data.json")

        catFile.writeText(gson.toJson(categories)) // ✅ Lưu object đầy đủ
        productFile.writeText(gson.toJson(products))
    }

    fun loadMenu(context: Context): Pair<List<Category>, List<Product>> {
        val catFile = File(context.filesDir, "catalog_data.json")
        val productFile = File(context.filesDir, "product_data.json")

        val categories = if (catFile.exists()) {
            val type = object : TypeToken<List<Category>>() {}.type
            gson.fromJson<List<Category>>(catFile.readText(), type)
        } else listOf(
            Category("Regular Phở", "#90CAF9"),
            Category("Nước", "#A5D6A7"),
            Category("Món Ăn Khác", "#F48FB1")
        )

        val products = if (productFile.exists()) {
            val type = object : TypeToken<List<Product>>() {}.type
            gson.fromJson<List<Product>>(productFile.readText(), type)
        } else emptyList()

        return Pair(categories, products)
    }

}
