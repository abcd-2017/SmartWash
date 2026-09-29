package com.smartwash.feature.order.impl.network.vo.order

import androidx.annotation.Keep
import com.smartwash.feature.order.api.model.OrderItemCount

/** 各状态订单计数响应 VO（原 app 的 network/vo/order/OrderItemCountVo，T6.1 迁入） */
@Keep
data class OrderItemCountVo(
    val pendingPaymentCount: Int,
    val processingCount: Int,
    val pendingPickupCount: Int,
    val shippedCount: Int,
)

/** 网络 VO → 对外领域模型（order-api 的 OrderItemCount） */
internal fun OrderItemCountVo.toModel() = OrderItemCount(
    pendingPaymentCount = pendingPaymentCount,
    processingCount = processingCount,
    pendingPickupCount = pendingPickupCount,
    shippedCount = shippedCount,
)
