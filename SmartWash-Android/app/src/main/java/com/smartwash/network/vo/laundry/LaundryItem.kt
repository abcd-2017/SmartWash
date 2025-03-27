package com.smartwash.network.vo.laundry

data class LaundryItem(
    val itemId: Long,
    val itemName: String,
    val basePrice: Float,
    val description: String
)