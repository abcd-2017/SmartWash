package com.smartwash.feature.readlater.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.readlater.ReadLaterRoute

/**
 * 稍后读路由聚合（模式同 toolboxGraph/moodGraph）：页面的 composable 注册自
 * app 壳 MainActivity NavHost，宿主侧一行 [readLaterGraph] 完成聚合。
 * 类型安全路由取模块内 [ReadLaterRoute]（单一事实来源）。
 * 入口：深链 smartwash://read-later（di/ReadLaterDeepLinkModule.kt 贡献）与工作台卡片。
 */
fun NavGraphBuilder.readLaterGraph(navController: NavHostController) {
    composable<ReadLaterRoute.Page> {
        ReadLaterPage(navController)
    }
}
