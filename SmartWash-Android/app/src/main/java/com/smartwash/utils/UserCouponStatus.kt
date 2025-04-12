package com.smartwash.utils

enum class UserCouponStatus(val status: String, val description: String) {
    OVERDUE("1", "已过期"),
    ACTIVE("0", "生效中")
}