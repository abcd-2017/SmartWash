package com.smartwash.feature.coupon.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 优惠券域类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class CouponRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"Coupon"），保证宿主 NavHost 注册与模块内跳转行为等价；
 * feature 不得反向依赖 app 壳层的 PageConstant。
 *
 * 消费方（均为导航诉求）：
 * - app 壳：NavHost 注册 + 首页服务网格「优惠券」跳转；
 * - user-impl：用户中心「优惠券」入口；
 * - payment-impl：支付页选券弹层「去领取」。
 */
sealed interface CouponRoute : SmartWashRoute {

    /** 优惠券页（三 tab：可领取 / 已领取 / 历史） */
    @Serializable
    @SerialName("Coupon")
    data object Coupon : CouponRoute
}
