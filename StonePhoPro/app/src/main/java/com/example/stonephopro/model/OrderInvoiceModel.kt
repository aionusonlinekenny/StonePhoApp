// OrderInvoiceModel.kt
package com.example.stonephopro.model

data class Invoice(
    val id: String,
    val items: List<Product>,
    val subtotal: Double,
    val discount : Double,
    val tax: Double,
    val total: Double,
    val date: String,
    val time: String,
    val tableTitle: String = ""   // số bàn (trống nếu là Quick Pay / POS thường)
)

