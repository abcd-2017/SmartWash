package com.smartwash.feature.memo.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.memo.MemoRoute
import com.smartwash.feature.memo.ui.memo.MemoPage

/**
 * 速记手账路由聚合（模式同 moodGraph/toolboxGraph）：单页面 composable 注册自
 * app 壳 MainActivity NavHost，宿主侧一行 [memoGraph] 完成聚合。类型安全路由取
 * 模块内 [MemoRoute]（单一事实来源）。
 */
fun NavGraphBuilder.memoGraph(navController: NavHostController) {
    composable<MemoRoute.Page> {
        MemoPage(navController)
    }
}

/** 深链/工作台入口 → 速记手账主页 */
fun NavHostController.navigateToMemo() {
    navigate(MemoRoute.Page)
}
