package com.example.stonephopro.utils.clover

import com.google.gson.annotations.SerializedName

data class CloverOrdersResponse(
    val elements: List<CloverOrder> = emptyList()
)

data class CloverOrder(
    val id: String = "",
    val total: Long = 0,                    // cents
    val state: String = "",
    val paymentState: String = "",          // OPEN | PARTIAL | FULL | CREDITED
    val title: String = "",
    val tableLabel: String? = null,
    val orderType: CloverOrderTypeRef? = null,
    @SerializedName("createdTime") val createdTime: Long = 0,
    val lineItems: CloverLineItemsWrapper? = null
)

data class CloverOrderTypeRef(
    val id: String = "",
    val label: String = ""                  // "Dine In", "To Go", v.v.
)

data class CloverLineItemsWrapper(
    val elements: List<CloverLineItem> = emptyList()
)

data class CloverLineItem(
    val id: String = "",
    val name: String = "",
    val price: Long = 0,                    // cents
    val unitQty: Int = 1000,                // 1000 = 1 đơn vị
    val item: CloverItemRef? = null
) {
    val quantity: Double get() = unitQty / 1000.0
    val lineTotal: Long get() = (price * quantity).toLong()
}

// Clover inventory item — đây là "MAPPING TABLE" (bàn map món ăn)
data class CloverItemRef(
    val id: String = "",
    val name: String = ""
)

// Bàn từ Clover table service
data class CloverTable(
    val id: String = "",
    val name: String = "",                  // Tên/số bàn
    val maxSeats: Int = 4
)

data class CloverTablesResponse(
    val elements: List<CloverTable> = emptyList()
)
