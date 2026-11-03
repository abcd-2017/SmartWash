package com.smartwash.feature.mood.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.mood.MoodRoute
import com.smartwash.feature.mood.ui.calendar.MoodCalendarPage
import com.smartwash.feature.mood.ui.today.MoodTodayPage
import com.smartwash.feature.mood.ui.trend.MoodTrendPage

/**
 * 情绪气象站路由聚合（模式同 toolboxGraph/divinationGraph）：3 页面的 composable 注册
 * 自 app 壳 MainActivity NavHost，宿主侧一行 [moodGraph] 完成聚合。
 * 路由常量取模块内 [MoodRoute]（单一事实来源）。当前宿主入口未接线，建议 Today 作入口。
 */
fun NavGraphBuilder.moodGraph(navController: NavHostController) {
    composable(MoodRoute.Today.text) {
        MoodTodayPage(navController)
    }
    composable(MoodRoute.Calendar.text) {
        MoodCalendarPage(navController)
    }
    composable(MoodRoute.Trend.text) {
        MoodTrendPage(navController)
    }
}

/** 趋势页空态 → 去今日记录（MoodRoute.Today 入栈） */
fun NavHostController.navigateToMoodToday() {
    navigate(MoodRoute.Today.text)
}

/** 今日记录 → 月历热图 */
fun NavHostController.navigateToMoodCalendar() {
    navigate(MoodRoute.Calendar.text)
}

/** 今日记录 → 月度趋势 */
fun NavHostController.navigateToMoodTrend() {
    navigate(MoodRoute.Trend.text)
}
