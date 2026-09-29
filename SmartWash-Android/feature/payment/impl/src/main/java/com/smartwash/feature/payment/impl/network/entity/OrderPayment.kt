package com.smartwash.feature.payment.impl.network.entity

import androidx.annotation.Keep

@Keep
data class OrderPayment(
    val orderId: Long,
    val paymentType: String,
    val userCouponId: Long?
)
