package com.smartwash.feature.order.api

/**
 * 取件/寄件类型（壳层取件域页面用）。
 * type 值委托 [OrderRoute.PICKUP_TYPE_*]（单一事实来源）。
 */
enum class PickupDeliveryType(val type: Int) {
    PICKUP(OrderRoute.PICKUP_TYPE_PICKUP),
    DELIVERY(OrderRoute.PICKUP_TYPE_DELIVERY)
}
