package com.smartwash.feature.order.impl.network.entity.order

import androidx.annotation.Keep

/** 寄件/取件请求体（原 app 的 network/entity/order/OrderNextStatus，T6.1 迁入） */
@Keep
data class OrderNextStatus(
    val orderId: Long,
    val pickupCode: String,
)
