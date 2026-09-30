package com.smartwash.common.ui.navigation

/**
 * 壳层路由契约（T8.1）：无域归属、注册在 app 壳且被多域 feature 跳转的路由常量。
 *
 * 主页壳（HOME）由 user-impl（登录/注册/资料保存成功进入、登出清栈）与
 * payment-impl（支付成功 popUpTo 回主页）跨模块消费——单一事实来源放公共层，
 * 依赖方向 feature → common 合法（feature 不得反向依赖 app 壳的 PageConstant）。
 * 域页面路由归各自 api 模块（UserRoute/OrderRoute/PaymentRoute/LaundryRoute/
 * CouponRoute）或 :feature:divination 的 DivRoute；app 壳留守页面常量在 PageConstant。
 */
object ShellRoute {

    /** 主页壳（HomePage，底部四 tab 容器；注册于壳层 shellGraph） */
    const val HOME = "Home"
}
