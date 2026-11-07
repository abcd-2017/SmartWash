package com.smartwash.feature.game.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.feature.game.GameRoute
import com.smartwash.feature.game.model.GameItem
import com.smartwash.feature.game.ui.center.GameCenterPage

/**
 * 游戏域路由聚合（模式同 toolboxGraph）：GameCenterPage 注册自 app 壳
 * MainActivity NavHost，宿主侧一行 [gameGraph] 完成聚合。类型安全路由取模块内
 * [GameRoute]（单一事实来源）。无底栏入口：进入路径为深链
 * smartwash://game-center（本模块 di/DeepLinkModule 贡献）与后端 workbench_entry
 * 配置的工作台卡片（target 指向同一深链）。
 */
fun NavGraphBuilder.gameGraph(navController: NavHostController) {
    composable<GameRoute.Center> {
        GameCenterPage(navController)
    }
}

/**
 * 游戏卡 → 内置浏览器：复用壳层 WebView 类型安全路由（[ShellRoute.WebView]，
 * 单一事实来源在 common:ui）。后端下发的 url 即最终可玩地址，端上零拼装；
 * url 为空的可见反馈（toast）由页面层承担，此处保留防御 return 兜底其他调用方。
 */
fun NavHostController.navigateToGameWebView(game: GameItem) {
    if (game.url.isBlank()) return
    navigate(
        ShellRoute.WebView(
            url = game.url,
            title = game.title,
        )
    )
}
