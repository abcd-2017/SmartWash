package com.smartwash.feature.memo

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 速记手账类型安全路由（模块内单一事实来源）。
 *
 * 形态照 :feature:mood/:feature:toolbox 先例：sealed interface + @Serializable 路由对象，
 * @SerialName 固定路由串；模块内页面跳转一律引用本类（feature 不得反向依赖 app 壳层的
 * PageConstant）。入口走深链 smartwash://quick-memo 与后端 workbench_entry 配置，
 * 壳层 NavHost 一行 memoGraph 聚合。
 */
sealed interface MemoRoute : SmartWashRoute {

    /** 速记手账主页（随手记输入区 + 记录卡片列表 + 标签筛选 + 搜索） */
    @Serializable
    @SerialName("Memo")
    data object Page : MemoRoute
}
