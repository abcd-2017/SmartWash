package com.smartwash.feature.review.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.feature.review.ReviewRoute
import com.smartwash.feature.review.ui.material.MaterialListPage
import com.smartwash.feature.review.ui.queue.ReviewQueuePage
import com.smartwash.feature.review.ui.study.ReviewStudyPage

/**
 * 记忆复习路由聚合（模式同 toolboxGraph）：3 页面的 composable 注册自 app 壳
 * MainActivity NavHost，宿主侧一行 [reviewGraph] 完成聚合（入口宿主后续接入，
 * 本次不改壳层导航）。路由常量取模块内 [ReviewRoute]（单一事实来源）。
 */
fun NavGraphBuilder.reviewGraph(navController: NavHostController) {
    composable(ReviewRoute.Queue.text) {
        ReviewQueuePage(navController)
    }
    // startCardId 走 query（LongType 缺省 -1 = 从队首开始），复习页经 SavedStateHandle 读取
    composable(
        route = "${ReviewRoute.Study.text}?${ReviewRoute.ARG_START_CARD_ID}={${ReviewRoute.ARG_START_CARD_ID}}",
        arguments = listOf(
            navArgument(ReviewRoute.ARG_START_CARD_ID) {
                type = NavType.LongType
                defaultValue = -1L
            },
        )
    ) {
        ReviewStudyPage(navController)
    }
    composable(ReviewRoute.Materials.text) {
        MaterialListPage(navController)
    }
}

/** 进入复习页（queuePage 队列条目点击时携带该卡 id 定位起始，缺省从队首） */
fun NavHostController.navigateToReviewStudy(startCardId: Long = -1L) {
    navigate("${ReviewRoute.Study.text}?${ReviewRoute.ARG_START_CARD_ID}=$startCardId")
}

/**
 * 数据变更信号：资料页建资料/建卡成功、复习页每次评分成功后调用，
 * 队列页（previousBackStackEntry）消费后复位并重拉今日队列。
 */
fun NavHostController.notifyReviewDataChanged() {
    previousBackStackEntry?.savedStateHandle?.set(ReviewRoute.CHANGED_KEY, true)
}
