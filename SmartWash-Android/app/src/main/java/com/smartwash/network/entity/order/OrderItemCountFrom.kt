package com.smartwash.network.entity.order

data class OrderItemCountFrom(
    val pendingPaymentStatus: String,
    val processingStatus: String,
    val pendingPickupStatus: String,
    val shippedStatus: String
)