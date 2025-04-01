package com.smartwash.network.vo.order

data class OrderItemCountVo(
    val pendingPaymentCount: Int,
    val processingCount: Int,
    val pendingPickupCount: Int,
    val shippedStatusCount: Int
)
