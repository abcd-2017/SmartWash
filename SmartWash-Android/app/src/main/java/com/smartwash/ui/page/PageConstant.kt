package com.smartwash.ui.page

import com.smartwash.common.ui.R as CommonUiR
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

    /** AI 工具工作台（壳层留守页面，注册于壳层 shellGraph；AI 接入为后续迭代） */
    data object AiWork : PageConstant("AiWork", "AI 工具工作台")
}

/**
 * 主页壳底部 tab 常量（HomePage 内 NavHost 注册 + BottomBar 渲染）。
 * tab 页面归属各域的，路由值委托各域 Route 常量（单一事实来源，T8.1）：
 * Service → [LaundryRoute.Service]、UserInfo → [UserRoute.UserInfo]；
 * Index / Workbench 为壳层留守 tab 页，常量壳层自持。
 * Workbench（设计稿屏 15）落地后观象台不再是 tab 直接落地页，经工作台特色卡
 * navigate(DivRoute.Home) 进入。
 */
sealed class HomePageConstant(
    val text: String,
    val description: String,
    val iconRes: Int,
    /** 规范 §4.5 实心路径（带镂空，EvenOdd 填充） */
    val solidPath: String,
    /** 规范 §4.5 外轮廓路径（勾边动效专用；无镂空图标与实心相同） */
    val outlinePath: String,
) {
    data object Index :
        HomePageConstant("Index", "首页", CommonUiR.drawable.ic_nav_home_solid,
            solidPath = "M12.96 2.35 21.46 9.88a1.5 1.5 0 0 1 .54 1.15V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-8.97a1.5 1.5 0 0 1 .54-1.15L11.04 2.35a1.5 1.5 0 0 1 1.92 0z M8 22v-6.5a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1V22z M10 22v-4.5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1V22z",
            outlinePath = "M12.96 2.35 21.46 9.88a1.5 1.5 0 0 1 .54 1.15V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-8.97a1.5 1.5 0 0 1 .54-1.15L11.04 2.35a1.5 1.5 0 0 1 1.92 0z",)

    data object Service :
        HomePageConstant(LaundryRoute.Service.text, "服务", CommonUiR.drawable.ic_nav_service_solid,
            solidPath = "M5 2h14a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3z M6.5 7h11a1 1 0 0 1 0 2h-11a1 1 0 0 1 0-2z M6.5 11h11a1 1 0 0 1 0 2h-11a1 1 0 0 1 0-2z M6.5 15h7a1 1 0 0 1 0 2h-7a1 1 0 0 1 0-2z",
            outlinePath = "M5 2h14a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3z",)

    // 工作台 tab（规范决策 7：替换问卜）— 三方块 + 一圆
    data object Workbench :
        HomePageConstant("Workbench", "工作台", CommonUiR.drawable.ic_nav_workbench_solid,
            solidPath = "M4 2h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M4 13h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z M15 13h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z M17.5 2a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9z",
            outlinePath = "M4 2h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M4 13h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z M15 13h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z M17.5 2a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9z",)

    data object UserInfo :
        HomePageConstant(UserRoute.UserInfo.text, "我的", CommonUiR.drawable.ic_nav_mine_solid,
            solidPath = "M12 2a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11z M2 19a5 5 0 0 1 5-5h10a5 5 0 0 1 5 5v1.5a1.5 1.5 0 0 1-1.5 1.5h-17A1.5 1.5 0 0 1 2 20.5V19z",
            outlinePath = "M12 2a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11z M2 19a5 5 0 0 1 5-5h10a5 5 0 0 1 5 5v1.5a1.5 1.5 0 0 1-1.5 1.5h-17A1.5 1.5 0 0 1 2 20.5V19z",)
}
