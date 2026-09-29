package com.smartwash.utils

import com.smartwash.feature.order.api.OrderRoute

/**
 * 取件/寄件类型（壳层取件域页面用）。
 * type 值委托 [OrderRoute.PICKUP_TYPE_*]（PickupDelivery 路由参数的单一事实来源，
 * T8.2 收敛双处定义；descriptionRes 零消费随 T8.2 删除）。
 */
enum class PickupDeliveryType(val type: Int) {
    PICKUP(OrderRoute.PICKUP_TYPE_PICKUP),
    DELIVERY(OrderRoute.PICKUP_TYPE_DELIVERY)
}
