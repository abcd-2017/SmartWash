package com.smartwash.feature.coupon

/**
 * 优惠券状态（T7.2 自 app 的 utils 迁入，取值不变）。
 * 归属调研：唯一消费方为本域可领取 tab（判断「已领取」态按钮），无跨模块消费方，
 * 故留 impl 不进 coupon-api（OrderStatus 迁 api 是因 order-impl 与 app 壳双侧消费）。
 */
enum class CouponStatus(val status: String) {
    EXPIRED("1"),
    ACTIVE("0"),
    RECEIVE("2")
}
