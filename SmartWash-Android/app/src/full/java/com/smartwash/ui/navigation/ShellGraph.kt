package com.smartwash.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.ai.AiWorkPage
import com.smartwash.ui.page.detail.OrderDetailPage
import com.smartwash.ui.page.home.HomePage
import com.smartwash.ui.page.pickup.PickupDeliveryPage
import com.smartwash.ui.page.pickup.PickupPage
import com.smartwash.ui.page.scan.ScanPage
import com.smartwash.ui.page.webview.WebViewPage

/**
 * 壳层留守页面路由聚合（T8.1）：主页壳（HomePage）+ 订单详情/寄件取件/取件/AI 工具台/
 * 内置浏览器五页面（T6.1/T7.1 调研结论：页面留 app 壳）的 composable 注册，自 MainActivity
 * NavHost 迁入。类型安全路由取值：主页壳/内置浏览器走 common:ui 的 [ShellRoute] 壳层契约；
 * 订单详情/寄件取件语义归订单域，走 order-api 的 [OrderRoute]；取件页/AI 工具台为
 * 壳层自有路由（[PageConstant.Pickup] / [PageConstant.AiWork]）。
 */
fun NavGraphBuilder.shellGraph(navController: NavHostController) {
    composable<ShellRoute.Home> {
        HomePage(navController)
    }
    composable<OrderRoute.OrderDetail> { entry ->
        OrderDetailPage(navController, entry.toRoute<OrderRoute.OrderDetail>().orderId)
    }
    composable<OrderRoute.PickupDelivery> { entry ->
        val route = entry.toRoute<OrderRoute.PickupDelivery>()
        PickupDeliveryPage(
            route.pickupType,
            navController,
            route.orderId,
        )
    }
    composable<PageConstant.Pickup> {
        PickupPage(navController)
    }
    // 全局扫一扫（工作台常驻入口）：识别 + 分发，业务承接在各域深链与壳层页面
    composable<ShellRoute.Scan> {
        ScanPage(navController)
    }
    composable<PageConstant.AiWork> {
        AiWorkPage(onBack = { navController.popBackStack() })
    }
    // 内置浏览器页：url 必填（仅 http/https，非法值页面内展示错误态）、title 可选；
    // chrome 可选（full/immersive/none，供游戏 H5 收起原生标题栏与状态栏，缺省 full）；
    // 字符串参数由 NavType 自动 URL 编码（完整 URL 含 / ? & 等保留字）
    composable<ShellRoute.WebView> { entry ->
        val route = entry.toRoute<ShellRoute.WebView>()
        WebViewPage(
            navController,
            url = route.url,
            title = route.title,
            chrome = route.chrome,
        )
    }
}
