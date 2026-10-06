package com.smartwash.ui.page

import com.smartwash.common.ui.R as CommonUiR
import com.smartwash.feature.divination.DivRoute
import com.smartwash.feature.laundry.api.LaundryRoute
import com.smartwash.feature.user.api.UserRoute

/**
 * 壳层页面路由常量（T8.1 拆路由后仅剩壳层留守、无域归属的页面）。
 *
 * 各域路由的单一事实来源已归位（宿主 NavHost 经 NavGraphBuilder.xxxGraph 聚合注册）：
 * 用户域 UserRoute（user-api）、订单域 OrderRoute（order-api，含留壳订单详情/寄件取件
 * 的常量）、支付域 PaymentRoute（payment-api）、洗衣域 LaundryRoute（laundry-api）、
 * 优惠券域 CouponRoute（coupon-api）、观象台 DivRoute（:feature:divination）、
 * 主页壳 ShellRoute（common:ui 壳层路由契约）。本类原有委托常量（Login/Register/
 * Recharge/Order/Div* 等）拆路由后零消费者，T8.1 删除。
 */
sealed class PageConstant(val text: String, val description: String) {
    /** 取件页（壳层留守页面，注册于壳层 shellGraph；取件域页面外迁时随迁） */
    data object Pickup : PageConstant("Pickup", "取件页面")
}

/**
 * 主页壳底部 tab 常量（HomePage 内 NavHost 注册 + BottomBar 渲染）。
 * tab 页面归属各域的，路由值委托各域 Route 常量（单一事实来源，T8.1）：
 * Service → [LaundryRoute.Service]、Divination → [DivRoute.Home]（T4.1 交接
 * 遗留的 "DivHome" 硬编码串收敛）、UserInfo → [UserRoute.UserInfo]；
 * Index 为壳层留守首页 tab，常量壳层自持。
 */
sealed class HomePageConstant(
    val text: String,
    val description: String,
    val iconRes: Int,
) {
    data object Index :
        HomePageConstant("Index", "首页", CommonUiR.drawable.ic_nav_home_solid)

    data object Service :
        HomePageConstant(LaundryRoute.Service.text, "服务", CommonUiR.drawable.ic_nav_service_solid)

    // 工作台（规范决策 7：替换问卜）— 三方块 + 一圆
    data object Divination :
        HomePageConstant(DivRoute.Home.text, "工作台", CommonUiR.drawable.ic_nav_workbench_solid)

    data object UserInfo :
        HomePageConstant(UserRoute.UserInfo.text, "我的", CommonUiR.drawable.ic_nav_mine_solid)
}
