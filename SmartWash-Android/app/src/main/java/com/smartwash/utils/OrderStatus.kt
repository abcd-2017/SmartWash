package com.smartwash.utils

import androidx.annotation.StringRes
import com.smartwash.R

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

enum class ShowOrderStatus(val status: String, @StringRes val descriptionRes: Int) {
    ALL_ORDER("001", R.string.order_status_all),
    PENDING_PAYMENT("0", R.string.order_status_pending_payment),
    PENDING_SHIPMENT("1", R.string.order_status_pending_shipment),
    WASHING("3", R.string.order_status_washing),
    READY_FOR_PICKUP("6", R.string.order_status_ready_for_pickup)
}
