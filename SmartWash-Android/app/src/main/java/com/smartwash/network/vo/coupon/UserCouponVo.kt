package com.smartwash.network.vo.coupon

data class UserCouponVo(
    val userCouponId: Long,
    val phoneNumber: String,
    val couponVo: CouponVo,
    val isUsed: Boolean,
    val expiredAt: String,
    val usedAt: String,
    val receivedAt: String,
    val orderNo: String,
)