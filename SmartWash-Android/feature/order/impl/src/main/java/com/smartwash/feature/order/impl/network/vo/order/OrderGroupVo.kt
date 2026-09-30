package com.smartwash.feature.order.impl.network.vo.order

import androidx.annotation.Keep

/** 订单分组响应 VO（订单页 summary 接口；原 app 的 network/vo/order/OrderGroupVo，T6.1 迁入） */
@Keep
data class OrderGroupVo(
    val items: List<OrderInfoVo>,
    val hasMore: Boolean,
    val total: Int
)
