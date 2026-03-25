package com.example.stonephopro.model

data class InventoryItem(
    val id: String,                // 6 số mã sản phẩm do người dùng nhập
    val quantity: Int,             // SL (số lượng)
    val unit: String,              // Đơn vị (BOX, CS, BAG, ...)
    val description: String,       // Tên sản phẩm mô tả
    val caseType: String,          // CT: 50LB, 12/28oz, 4x1GAL, ...
    val price: Double,             // Đơn giá
    val subtotal: Double,          // Tính = price * quantity
    val date: String ,             // Ngày nhập theo định dạng MM-dd-yyyy
    val type: String ,              // ✅ DRY / FRZ / REF
    val slc: Int? = null
)
