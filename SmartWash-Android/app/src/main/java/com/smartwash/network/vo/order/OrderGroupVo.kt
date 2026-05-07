package com.smartwash.network.vo.order

import androidx.annotation.Keep

@Keep
data class OrderGroupVo(
    val items: List<OrderInfo>,
    val hasMore: Boolean,
    val total: Int
)
