package com.smartwash.network.entity

data class OrderPayment(val orderId: Long, val amount: Float, val paymentType: String)