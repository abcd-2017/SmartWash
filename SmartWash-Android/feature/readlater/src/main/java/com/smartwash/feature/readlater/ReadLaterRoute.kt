package com.smartwash.feature.readlater

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 稍后读（AI 摘要）类型安全路由（模块内单一事实来源）。
 *
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串
 * （"ReadLater"）；feature 不得反向依赖 app 壳层的 PageConstant。
 *
 * 入口：深链 smartwash://read-later（见 di/ReadLaterDeepLinkModule.kt）与
 * 后端 workbench_entry 配置的工作台卡片，无底栏入口；
 * 接入方式 = app 壳一行 `readLaterGraph(navController)`。
 */
sealed interface ReadLaterRoute : SmartWashRoute {

    /** 稍后读列表页（收藏输入条 + 未读/已读 Tab + 搜索/标签筛选 + 卡片列表） */
    @Serializable
    @SerialName("ReadLater")
    data object Page : ReadLaterRoute
}
