package com.smartwash.utils

enum class CouponStatus(val status: String, val description: String) {
    EXPIRED("1", "已失效"),
    ACTIVE("0", "生效中"),
    RECEIVE("2", "已领取")
}