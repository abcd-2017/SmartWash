package com.smartwash.feature.order.impl.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.order.impl.ui.order.OrderPage

/**
 * 订单域路由聚合（T8.1）：订单页 composable 注册自 app 壳 MainActivity NavHost
 * 迁入，宿主侧一行 [orderGraph] 完成聚合。类型安全路由取 order-api 的 [OrderRoute]
 * （单一事实来源；订单详情/寄件取件页面留 app 壳，其常量同在 OrderRoute，
 * 注册归壳层 shellGraph）。
 */
fun NavGraphBuilder.orderGraph(navController: NavHostController) {
    composable<OrderRoute.Order> { entry ->
        OrderPage(navController, entry.toRoute<OrderRoute.Order>().itemId)
    }
}
