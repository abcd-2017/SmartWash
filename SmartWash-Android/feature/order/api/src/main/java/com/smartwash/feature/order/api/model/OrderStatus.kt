package com.smartwash.feature.order.api.model

import androidx.annotation.StringRes
import com.smartwash.feature.order.api.R

/**
 * 订单状态全集（后端状态码契约，T6.1 自 app 的 utils/OrderStatus.kt 迁入）。
 *
 * 归属说明：状态码是订单域对外契约的一部分——app 壳留守页面（index/detail/pickup）
 * 与 T6.3 后的 user-impl 计数查询都消费，单一事实来源放 order-api 避免双份漂移；
 * 订单状态文案（order_status_*）随枚举迁入本模块 res。
 */
enum class OrderStatus(val status: String, @StringRes val descriptionRes: Int) {
    CANCELED("-2", R.string.order_status_cancelled),
    REFUNDED("-1", R.string.order_status_refunded),
    PENDING_PAYMENT("0", R.string.order_status_pending_payment),
    PENDING_SHIPMENT("1", R.string.order_status_pending_shipment),
    RECEIVED("2", R.string.order_status_received),
    WASHING("3", R.string.order_status_washing),
    DRIED("4", R.string.order_status_dried),
    IN_DELIVERY("5", R.string.order_status_in_delivery),
    READY_FOR_PICKUP("6", R.string.order_status_ready_for_pickup),
    COMPLETED("7", R.string.order_status_completed);

    companion object {
        private val statusMap = entries.associateBy { it.status }
        fun fromStatus(status: String): OrderStatus? = statusMap[status]
        fun getDescriptionResByStatus(status: String): Int = fromStatus(status)?.descriptionRes ?: R.string.order_status_all
    }
}

/**
 * 订单页 tab 展示的状态子集（含虚拟的"全部"tab，T6.1 自 app 的 utils/OrderStatus.kt 迁入）。
 *
 * 消费方：订单页 tab（order-impl 内部）与用户中心订单计数查询的状态参数
 * （app 壳 seam → T6.3 起由 user-impl 经 OrderApi.getOrderItemCount 消费）。
 */
enum class ShowOrderStatus(val status: String, @StringRes val descriptionRes: Int) {
    ALL_ORDER("001", R.string.order_status_all),
    PENDING_PAYMENT("0", R.string.order_status_pending_payment),
    PENDING_SHIPMENT("1", R.string.order_status_pending_shipment),
    WASHING("3", R.string.order_status_washing),
    READY_FOR_PICKUP("6", R.string.order_status_ready_for_pickup)
}
