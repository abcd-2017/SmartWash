package com.smartwash.feature.payment.api

/**
 * 支付域路由常量（模块内单一事实来源，模式仿 OrderRoute / UserRoute / DivRoute）。
 *
 * 路由值对齐 app 壳层 PageConstant 的现有常量（PageConstant 侧已改为委托本类，
 * 保证宿主 NavHost 注册与模块内跳转永不漂移）；feature 不得反向依赖 app 壳层
 * 的 PageConstant。
 *
 * 消费方（均为导航诉求，无数据依赖）：
 * - order-impl：订单页「去支付」跳 Payment（T6.2 起替换其 HostRoutes.PAYMENT 过渡字面量）；
 * - user-impl：用户中心「去充值」跳 Recharge（T6.2 起替换其 HostRoutes.RECHARGE 过渡字面量）；
 * - payment-impl：域内页面互跳（支付页→支付成功/充值页、充值页→充值记录页）；
 * - app 壳：NavHost 注册（PageConstant 委托）+ 首页/洗衣预约页跳转。
 */
sealed class PaymentRoute(val text: String) {
    /** 支付页（跳转时拼 /{orderId} 参数） */
    data object Payment : PaymentRoute("Payment")

    /** 支付成功页（跳转时拼 /{orderId} 参数） */
    data object PaySuccess : PaymentRoute("PaySuccess")

    /** 充值页 */
    data object Recharge : PaymentRoute("Recharge")

    /** 充值记录页 */
    data object RechargeRecord : PaymentRoute("RechargeRecord")
}
