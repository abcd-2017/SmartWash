package com.smartwash.feature.coupon.api

import com.smartwash.feature.coupon.api.model.UsableCoupon

/**
 * 优惠券域对外服务契约（T7.2 自 payment-impl 的 CouponSource 过渡 seam 升格为正式契约，
 * 命名对齐 user-api 的 UserApi / order-api 的 OrderApi 先例）。
 *
 * 消费方：payment-impl 的支付页（PaymentViewModel 选券注入本接口，不得触碰
 * :feature:coupon:impl 的 CouponRepository 实现细节）；实现为 coupon 域的
 * CouponApiImpl（Hilt `@Binds`，di/CouponModule），依赖方向 payment-impl → coupon:api。
 */
interface CouponApi {

    /**
     * 查询订单可用优惠券（语义 = CouponRepository.getCanUseCoupon：data 为 null
     * 抛 NetworkException，由消费方并入错误状态）。
     */
    suspend fun getCanUseCoupon(orderId: Long): List<UsableCoupon>
}
