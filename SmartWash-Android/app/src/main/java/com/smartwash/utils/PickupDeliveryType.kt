package com.smartwash.utils

enum class PickupDeliveryType(val type: Int, val description: String) {
    PICKUP(0, "取件"), // 取件
    DELIVERY(1, "寄件") // 寄件
}