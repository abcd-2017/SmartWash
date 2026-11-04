package com.smartwash.feature.mood

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 情绪气象站类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class MoodRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"MoodToday"/"MoodCalendar"/"MoodTrend"）；模块内页面跳转一律引用
 * 本类（feature 不得反向依赖 app 壳层的 PageConstant）。
 *
 * 当前无任何入口指向本路由（宿主入口未分配），三页面仅作为独立路由注册在
 * 宿主 NavHost：接入方式 = app 壳一行 `moodGraph(navController)`。
 */
sealed interface MoodRoute : SmartWashRoute {

    /** 今日记录页（一次点按即存，建议作为宿主入口） */
    @Serializable
    @SerialName("MoodToday")
    data object Today : MoodRoute

    /** 月历热图页（dayKey 查表着色，点格子看当日详情） */
    @Serializable
    @SerialName("MoodCalendar")
    data object Calendar : MoodRoute

    /** 月度趋势页（等级分布 + 记录天数 + 月均值） */
    @Serializable
    @SerialName("MoodTrend")
    data object Trend : MoodRoute
}
