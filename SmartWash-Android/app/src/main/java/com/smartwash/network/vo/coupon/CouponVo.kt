package com.smartwash.network.vo.coupon

import androidx.annotation.Keep

@Keep
data class CouponVo(
    val couponId: Long,
    val title: String,
    val description: String,
    val discount: Float,
    val threshold: Float,
    val startTime: String,
    val endTime: String,
    val validDays: Int,
    val isNewUser: Boolean,
    val status: String,
)
