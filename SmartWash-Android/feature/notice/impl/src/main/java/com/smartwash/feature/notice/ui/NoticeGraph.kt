package com.smartwash.feature.notice.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.notice.api.NoticeRoute
import com.smartwash.feature.notice.ui.inbox.NoticeInboxPage

/**
 * 站内信域路由聚合（模式同 couponGraph/pointsGraph）：收件箱页 composable 注册自
 * app 壳 MainActivity NavHost 迁入，宿主侧一行 [noticeGraph] 完成聚合。
 * 类型安全路由取 notice-api 的 [NoticeRoute]（单一事实来源）。
 */
fun NavGraphBuilder.noticeGraph(navController: NavHostController) {
    composable<NoticeRoute.Inbox> { NoticeInboxPage(navController) }
}
