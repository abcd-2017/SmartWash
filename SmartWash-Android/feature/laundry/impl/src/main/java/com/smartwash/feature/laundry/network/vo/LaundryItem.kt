package com.smartwash.feature.laundry.network.vo

import androidx.annotation.Keep

@Keep
data class LaundryItem(
    val itemId: Long,
    val itemName: String,
    val basePrice: Float,
    val description: String,
)
