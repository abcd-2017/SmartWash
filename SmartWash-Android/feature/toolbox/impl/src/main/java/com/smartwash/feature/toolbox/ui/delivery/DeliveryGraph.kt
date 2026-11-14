package com.smartwash.feature.toolbox.ui.delivery

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.toolbox.api.ToolboxRoute

/**
 * 设备投递路由聚合（独立新函数，**不改动**既有 ToolboxGraph 路由表与 app 壳导航——
 * 导航入口一律不加，由使用方后续分配）：接入时在宿主 NavHost 追加一行
 * `deliveryGraph(navController)`（可与 toolboxGraph 并列），入口跳转
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
