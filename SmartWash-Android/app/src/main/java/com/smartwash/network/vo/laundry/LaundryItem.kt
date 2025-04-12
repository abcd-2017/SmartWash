package com.smartwash.network.vo.laundry

import androidx.annotation.Keep

@Keep
data class LaundryItem(
    val itemId: Long,
    val itemName: String,
    val basePrice: Float,
    val description: String,
)