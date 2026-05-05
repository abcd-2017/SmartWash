package com.smartwash.network.vo.coupon

import androidx.annotation.Keep

@Keep
data class AllCouponsVo(
    val available: List<CouponVo>,
    val claimed: List<UserCouponVo>,
    val historical: List<UserCouponVo>,
)
