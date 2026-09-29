package com.smartwash.feature.coupon.api

/**
 * 优惠券域路由常量（模块内单一事实来源，模式仿 LaundryRoute / PaymentRoute / OrderRoute）。
 *
 * 路由值对齐 app 壳层 PageConstant 的现有常量（PageConstant 侧已改为委托本类，
 * 保证宿主 NavHost 注册与模块内跳转永不漂移）；feature 不得反向依赖 app 壳层
 * 的 PageConstant。
 *
 * 消费方（均为导航诉求）：
 * - app 壳：NavHost 注册（PageConstant 委托）+ 首页服务网格「优惠券」跳转（经 PageConstant）；
 * - user-impl：用户中心「优惠券」入口（T7.2 起替换 HostRoutes.COUPON 过渡字面量）；
 * - payment-impl：支付页选券弹层「去领取」（T7.2 起替换 HostRoutes.COUPON 过渡字面量）。
 */
sealed class CouponRoute(val text: String) {

    /** 优惠券页（三 tab：可领取 / 已领取 / 历史） */
    data object Coupon : CouponRoute("Coupon")
}
