package com.smartwash.feature.coupon.repository

import com.smartwash.feature.coupon.api.CouponApi
import com.smartwash.feature.coupon.api.model.UsableCoupon
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 优惠券域对外服务（T7.2 自 app/di/PaymentImplSeamModule 的 CouponSourceImpl 升格迁入，
 * 实现语义不变：CouponRepository.getCanUseCoupon + UserCouponVo 收窄映射为 UsableCoupon，
 * 类型面收敛为支付页选券消费的四个字段，不泄漏优惠券域 VO）。
 */
@Singleton
class CouponApiImpl @Inject constructor(
    private val couponRepository: CouponRepository,
) : CouponApi {

    override suspend fun getCanUseCoupon(orderId: Long): List<UsableCoupon> {
        return couponRepository.getCanUseCoupon(orderId).map(::toUsableCoupon)
    }

    private fun toUsableCoupon(vo: UserCouponVo) = UsableCoupon(
        userCouponId = vo.userCouponId,
        discount = vo.couponVo.discount,
        threshold = vo.couponVo.threshold,
        expiredAt = vo.expiredAt,
    )
}
