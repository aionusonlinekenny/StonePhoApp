// Product.kt
package com.example.stonephopro.model

data class Product(
    val id: Int,
    val name: String,
    val price: Double,
    val category: String,
    val note: String? = null,
    val colorHex: String = "#ECEFF1"
)