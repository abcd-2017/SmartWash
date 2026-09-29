package com.smartwash.feature.order.api.model

/**
 * 进行中订单简要模型（原 app 的 network/vo/order/OrderVo，T6.1 迁入并去 Vo 后缀，
 * 对齐 user-api 的 UserInfo 命名）。
 *
 * 消费方：首页"进行中订单"卡片（orderInfo 全量快照走 [OrderInfo]，本模型仅承载
 * getWashingOrder 的轻量列表）。字段与原 OrderVo 完全一致，含默认值。
 */
data class OrderBrief(
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
