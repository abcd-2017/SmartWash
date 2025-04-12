package com.smartwash.network.entity

data class OrderPayment(val orderId: Long, val paymentType: String, val userCouponId: Long?)