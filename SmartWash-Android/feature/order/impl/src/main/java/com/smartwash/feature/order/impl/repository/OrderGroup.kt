package com.smartwash.feature.order.impl.repository

import com.smartwash.feature.order.api.model.OrderInfo

/**
 * 订单分组模型（订单页 summary 接口的领域形态，T6.1 新增——原 OrderGroupVo 直接透出，
 * 迁移后网络 VO 不出 network 层，Repository 映射为本类供订单页消费）。
 *
 * 仅 order-impl 内部使用（订单页 tab 分组加载），无跨模块消费者，故不进 order-api。
 */
data class OrderGroup(
    val items: List<OrderInfo>,
    val hasMore: Boolean,
    val total: Int,
)
