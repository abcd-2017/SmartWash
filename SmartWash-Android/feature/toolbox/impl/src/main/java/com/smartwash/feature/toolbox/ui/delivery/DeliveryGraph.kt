package com.smartwash.feature.toolbox.ui.delivery

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.toolbox.api.ToolboxRoute

/**
 * 设备投递路由聚合（独立函数，与 [toolboxGraph] 并列）：经 ToolboxGraphModule 以
 * `NavGraphContributor @IntoSet` 汇入壳层 NavHost（不再由壳层静态调用），入口跳转
 * ToolboxRoute.Delivery。类型安全路由单一事实来源在 [ToolboxRoute]。
 */
fun NavGraphBuilder.deliveryGraph(navController: NavHostController) {
    composable<ToolboxRoute.Delivery> {
        DeliveryCreatePage(navController)
    }
    composable<ToolboxRoute.DeliverySession> {
        DeliverySessionPage(navController)
    }
}

/** 发起页 → 会话页（会话码随路由携带）；launchSingleTop 防连点叠加多层会话页 */
fun NavHostController.navigateToDeliverySession(code: String) {
    navigate(ToolboxRoute.DeliverySession(code)) {
        launchSingleTop = true
    }
}
