package com.smartwash.network.entity.order

import androidx.annotation.Keep

@Keep
data class OrderNextStatus(
    val orderId: Long,
    val pickupCode: String,
)
