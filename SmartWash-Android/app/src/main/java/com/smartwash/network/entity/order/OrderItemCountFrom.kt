package com.smartwash.network.entity.order

import androidx.annotation.Keep

@Keep
data class OrderItemCountFrom(
    val pendingPaymentStatus: String,
    val processingStatus: String,
    val pendingPickupStatus: String,
    val shippedStatus: String
)