package com.smartwash.network.vo.order

data class OrderVo(
    val orderId: Long,
    val userId: Long,
    val schoolId: Long,
    val lockerId: Long,
    val orderNo: String,
    val laundryItemsId: Long,
    val totalPrice: Float,
    val status: String,
    val pickupCode: String,
    val createdAt: String,
    val updatedAt: String,
)
