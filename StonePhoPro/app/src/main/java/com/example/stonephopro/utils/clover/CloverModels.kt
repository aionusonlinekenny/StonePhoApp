package com.example.stonephopro.utils.clover

import com.google.gson.annotations.SerializedName

data class CloverOrdersResponse(
    val elements: List<CloverOrder> = emptyList()
)

data class CloverOrder(
    val id: String = "",
    val total: Long = 0,          // cents
    val state: String = "",
    val title: String = "",
    @SerializedName("createdTime") val createdTime: Long = 0,
    val lineItems: CloverLineItemsWrapper? = null
)

data class CloverLineItemsWrapper(
    val elements: List<CloverLineItem> = emptyList()
)

// Mỗi line item = một món trong order, tham chiếu đến item trong MAPPING TABLE (Clover inventory)
data class CloverLineItem(
    val id: String = "",
    val name: String = "",
    val price: Long = 0,          // cents
    val unitQty: Int = 1000,      // Clover unitQty: 1000 = 1 đơn vị
    val item: CloverItemRef? = null
) {
    val quantity: Double get() = unitQty / 1000.0
    val lineTotal: Long get() = (price * quantity).toLong()
}

// Tham chiếu đến Clover inventory item (MAPPING TABLE)
data class CloverItemRef(
    val id: String = "",
    val name: String = ""
)
