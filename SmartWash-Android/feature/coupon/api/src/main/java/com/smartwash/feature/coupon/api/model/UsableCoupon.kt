package com.smartwash.feature.coupon.api.model

/**
 * 订单可用优惠券（T7.2 自 payment-impl 的 PaymentCoupon 迁入并改名——契约归
 * coupon 域后不再带支付域前缀，模式同 laundry-api 的 SchoolOption；字段不变，
 * 只保留支付页选券消费的字段）。
 * 刻意不带 @Keep：本模型是 CouponApi 的领域载荷，不直接参与 Gson 反序列化
 * （网络层的 UserCouponVo 仍在 coupon 内经 Retrofit 解析后映射到本类）。
 */
data class UsableCoupon(
    val userCouponId: Long,
    val discount: Float,
    val threshold: Float,
    val expiredAt: String,
)
