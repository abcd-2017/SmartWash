package com.smartwash.network.vo.order

import androidx.annotation.Keep

@Keep
data class OrderVo(
    val orderId: Long,
    val userId: Long = 0,
    val schoolId: Long = 0,
    val lockerId: Long = 0,
    val orderNo: String,
    val laundryItemsId: Long = 0,
    val totalPrice: Float = 0f,
    val payPrice: Float = 0f,
    val status: String,
    val pickupCode: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)
