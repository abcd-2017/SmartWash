package com.smartwash.utils

enum class OrderStatus(val status: String, val description: String) {
    CANCELED("-2", "已取消"),
    REFUNDED("-1", "已退款"),
    PENDING_PAYMENT("0", "待支付"),
    PENDING_SHIPMENT("1", "待寄件"),
    RECEIVED("2", "已收取"),
    WASHING("3", "清洗中"),
    DRIED("4", "已烘干"),
    IN_DELIVERY("5", "配送中"),
    READY_FOR_PICKUP("6", "待取件"),
    COMPLETED("7", "已完成");

    companion object {
        private val statusMap = entries.associateBy { it.status }
        fun fromStatus(status: String): OrderStatus? = statusMap[status]
        fun getDescriptionByStatus(status: String): String? = fromStatus(status)?.description
    }
}

enum class ShowOrderStatus(val status: String, val description: String) {
    ALL_ORDER("001", "全部订单"),
    PENDING_PAYMENT("0", "待支付"),
    PENDING_SHIPMENT("1", "待寄件"),
    WASHING("3", "清洗中"),
    READY_FOR_PICKUP("6", "待取件")
}