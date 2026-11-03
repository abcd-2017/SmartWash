package com.smartwash.feature.mood

/**
 * 情绪气象站路由常量（模块内单一事实来源，模式同 ToolboxRoute/DivRoute）。
 *
 * 模块内页面跳转一律引用本常量（feature 不得反向依赖 app 壳层的 PageConstant）；
 * 当前无任何入口指向本路由（宿主入口未分配），三页面仅作为独立路由注册在
 * 宿主 NavHost：接入方式 = app 壳一行 `moodGraph(navController)`（见 ui/MoodGraph.kt）。
 */
sealed class MoodRoute(val text: String) {

    /** 今日记录页（一次点按即存，建议作为宿主入口） */
    data object Today : MoodRoute("MoodToday")

    /** 月历热图页（dayKey 查表着色，点格子看当日详情） */
    data object Calendar : MoodRoute("MoodCalendar")

    /** 月度趋势页（等级分布 + 记录天数 + 月均值） */
    data object Trend : MoodRoute("MoodTrend")
}
