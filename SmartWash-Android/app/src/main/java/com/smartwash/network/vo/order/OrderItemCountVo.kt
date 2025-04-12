package com.smartwash.network.vo.order

import androidx.annotation.Keep

@Keep
data class OrderItemCountVo(
    val pendingPaymentCount: Int,
    val processingCount: Int,
    val pendingPickupCount: Int,
    val shippedStatusCount: Int,
)
