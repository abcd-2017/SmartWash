package com.smartwash.feature.order.api.model

/**
 * 各状态订单计数（原 app 的 network/vo/order/OrderItemCountVo，T6.1 迁入并去 Vo 后缀）。
 *
 * 消费方：用户中心订单快捷入口（app 壳 seam → T6.3 起由 user-impl 经 OrderApi 直取）。
 */
data class OrderItemCount(
    val pendingPaymentCount: Int,
    val processingCount: Int,
    val pendingPickupCount: Int,
    val shippedCount: Int,
)
