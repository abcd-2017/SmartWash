package com.smartwash.feature.points.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.points.api.PointsRoute
import com.smartwash.feature.points.ui.points.PointsPage

/**
 * 积分域路由聚合（模式同 couponGraph/moodGraph）：签到有礼页 composable 注册自
 * app 壳 MainActivity NavHost 迁入，宿主侧一行 [pointsGraph] 完成聚合。
 * 类型安全路由取 points-api 的 [PointsRoute]（单一事实来源）。
 */
fun NavGraphBuilder.pointsGraph(navController: NavHostController) {
    composable<PointsRoute.Home> { PointsPage(navController) }
}
