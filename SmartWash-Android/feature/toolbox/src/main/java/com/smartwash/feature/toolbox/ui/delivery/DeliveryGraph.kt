package com.smartwash.feature.toolbox.ui.delivery

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.feature.toolbox.ToolboxRoute

/**
 * 设备投递路由聚合（独立新函数，**不改动**既有 ToolboxGraph 路由表与 app 壳导航——
 * 导航入口一律不加，由使用方后续分配）：接入时在宿主 NavHost 追加一行
 * `deliveryGraph(navController)`（可与 toolboxGraph 并列），入口跳转
 * ToolboxRoute.Delivery。路由常量单一事实来源在 [ToolboxRoute]。
 */
fun NavGraphBuilder.deliveryGraph(navController: NavHostController) {
    composable(ToolboxRoute.Delivery.text) {
        DeliveryCreatePage(navController)
    }
    // 会话码经 query 携带并 Uri.encode（对齐短链详情先例的字符串参数防御）
    composable(
        route = "${ToolboxRoute.DeliverySession.text}?${DeliverySessionViewModel.ARG_CODE}={${DeliverySessionViewModel.ARG_CODE}}",
        arguments = listOf(
            navArgument(DeliverySessionViewModel.ARG_CODE) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        DeliverySessionPage(navController)
    }
}

/** 发起页 → 会话页（会话码随路由携带） */
fun NavHostController.navigateToDeliverySession(code: String) {
    navigate("${ToolboxRoute.DeliverySession.text}?${DeliverySessionViewModel.ARG_CODE}=${Uri.encode(code)}")
}
