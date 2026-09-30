package com.smartwash.feature.divination.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.feature.divination.DivRoute
import com.smartwash.feature.divination.ui.page.ask.DivAskPage
import com.smartwash.feature.divination.ui.page.cast.DivCastPage
import com.smartwash.feature.divination.ui.page.chart.DivChartPage
import com.smartwash.feature.divination.ui.page.followup.DivFollowUpPage
import com.smartwash.feature.divination.ui.page.history.DivHistoryPage
import com.smartwash.feature.divination.ui.page.home.DivHomePage
import com.smartwash.feature.divination.ui.page.reading.DivReadingPage

/**
 * 观象台路由聚合（T8.1）：7 页面的 composable 注册自 app 壳 MainActivity
 * NavHost 迁入，宿主侧一行 [divinationGraph] 完成聚合。路由常量取模块内
 * [DivRoute]（单一事实来源；DivHome 同为主页底部问卜 tab 路由，壳层
 * HomePageConstant.Divination 委托其值）。
 */
fun NavGraphBuilder.divinationGraph(navController: NavHostController) {
    composable(DivRoute.Home.text) {
        DivHomePage(navController)
    }
    composable(
        route = "${DivRoute.Ask.text}?method={method}",
        arguments = listOf(
            navArgument("method") {
                type = NavType.StringType
                defaultValue = "liuyao"
            }
        )
    ) { entity ->
        DivAskPage(navController, entity.arguments?.getString("method") ?: "liuyao")
    }
    composable(
        route = "${DivRoute.Cast.text}?question={question}&category={category}",
        arguments = listOf(
            navArgument("question") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("category") {
                type = NavType.StringType
                defaultValue = "other"
            }
        )
    ) { entity ->
        DivCastPage(
            navController,
            question = entity.arguments?.getString("question").orEmpty(),
            categoryId = entity.arguments?.getString("category") ?: "other",
        )
    }
    composable(
        route = "${DivRoute.Chart.text}/{recordId}?animate={animate}",
        arguments = listOf(
            navArgument("recordId") {
                type = NavType.LongType
            },
            navArgument("animate") {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { entity ->
        DivChartPage(
            navController,
            recordId = entity.arguments?.getLong("recordId") ?: -1L,
            animateEntry = entity.arguments?.getBoolean("animate") ?: false,
        )
    }
    composable(DivRoute.History.text) {
        DivHistoryPage(navController)
    }
    composable(
        route = "${DivRoute.Reading.text}/{recordId}",
        arguments = listOf(
            navArgument("recordId") {
                type = NavType.LongType
            }
        )
    ) { entity ->
        DivReadingPage(
            navController,
            recordId = entity.arguments?.getLong("recordId") ?: -1L,
        )
    }
    composable(
        route = "${DivRoute.FollowUp.text}/{recordId}",
        arguments = listOf(
            navArgument("recordId") {
                type = NavType.LongType
            }
        )
    ) { entity ->
        DivFollowUpPage(
            navController,
            recordId = entity.arguments?.getLong("recordId") ?: -1L,
        )
    }
}
