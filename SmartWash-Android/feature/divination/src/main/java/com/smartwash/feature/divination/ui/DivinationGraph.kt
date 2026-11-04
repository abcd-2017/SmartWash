package com.smartwash.feature.divination.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
 * NavHost 迁入，宿主侧一行 [divinationGraph] 完成聚合。类型安全路由取模块内
 * [DivRoute]（单一事实来源）。底栏工作台 tab 落地壳层 WorkbenchPage
 * （设计稿屏 15）后，DivHome 由工作台特色卡/卦历等入口 navigate 进入。
 */
fun NavGraphBuilder.divinationGraph(navController: NavHostController) {
    composable<DivRoute.Home> {
        DivHomePage(navController)
    }
    composable<DivRoute.Ask> { entry ->
        DivAskPage(navController, entry.toRoute<DivRoute.Ask>().method)
    }
    composable<DivRoute.Cast> { entry ->
        val route = entry.toRoute<DivRoute.Cast>()
        DivCastPage(
            navController,
            question = route.question,
            categoryId = route.category,
        )
    }
    composable<DivRoute.Chart> { entry ->
        val route = entry.toRoute<DivRoute.Chart>()
        DivChartPage(
            navController,
            recordId = route.recordId,
            animateEntry = route.animate,
        )
    }
    composable<DivRoute.History> {
        DivHistoryPage(navController)
    }
    composable<DivRoute.Reading> { entry ->
        DivReadingPage(
            navController,
            recordId = entry.toRoute<DivRoute.Reading>().recordId,
        )
    }
    composable<DivRoute.FollowUp> { entry ->
        DivFollowUpPage(
            navController,
            recordId = entry.toRoute<DivRoute.FollowUp>().recordId,
        )
    }
}
