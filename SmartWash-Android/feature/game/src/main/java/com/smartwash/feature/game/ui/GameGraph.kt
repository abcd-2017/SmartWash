package com.smartwash.feature.game.ui

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.feature.game.GameRoute
import com.smartwash.feature.game.model.GameItem
import com.smartwash.feature.game.ui.center.GameCenterPage

/**
 * 游戏域路由聚合（模式同 toolboxGraph）：GameCenterPage 注册自 app 壳
 * MainActivity NavHost，宿主侧一行 [gameGraph] 完成聚合。路由常量取模块内
 * [GameRoute]（单一事实来源）。无底栏入口：进入路径为深链
 * smartwash://game-center（本模块 di/DeepLinkModule 贡献）与后端 workbench_entry
 * 配置的工作台卡片（target 指向同一深链）。
 */
fun NavGraphBuilder.gameGraph(navController: NavHostController) {
    composable(GameRoute.Center.text) {
        GameCenterPage(navController)
    }
}

/**
 * 游戏卡 → 内置浏览器：复用壳层既有 WebView 路由（注册形态 =
 * ShellRoute.WEB_VIEW + "?url={url}&title={title}"，单一事实来源在 common:ui 的
 * [ShellRoute]；url/title 必须经 Uri.encode 编码，约定同 DeepLinkHandler/工具箱）。
 * 后端下发的 url 即最终可玩地址，端上零拼装；url 为空不响应（后端契约保证非空，
 * 此处防御）。
 */
fun NavHostController.navigateToGameWebView(game: GameItem) {
    if (game.url.isBlank()) return
    val builder = StringBuilder(ShellRoute.WEB_VIEW)
        .append("?url=")
        .append(Uri.encode(game.url))
    if (game.title.isNotBlank()) {
        builder.append("&title=").append(Uri.encode(game.title))
    }
    navigate(builder.toString())
}
