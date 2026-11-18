package com.smartwash.feature.order.api.model

/**
 * 订单评价模型（对齐后端 ReviewVo）。
 *
 * 消费方：订单详情页"已完成"状态的评价入口（提交 + 回显本人已提交评价）。
 */
data class OrderReview(
    val reviewId: Long = 0,
    val orderId: Long = 0,
    val userId: Long = 0,
    val userPhone: String = "",
    val rating: Int = 0,
    val content: String = "",
    val createdAt: String = "",
)
