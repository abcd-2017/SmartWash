package com.smartwash.feature.game

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 游戏域类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class GameRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"GameCenter"）；模块内页面跳转一律引用本类（feature 不得反向依赖
 * app 壳层的 PageConstant）。
 *
 * 当前无底栏入口，页面仅作为独立路由注册在宿主 NavHost，入口为
 * 深链 smartwash://game-center（本模块 di/DeepLinkModule 贡献）与后端
 * workbench_entry 配置的工作台卡片（target 指向同一深链）。
 */
sealed interface GameRoute : SmartWashRoute {

    /** 游戏中心列表页 */
    @Serializable
    @SerialName("GameCenter")
    data object Center : GameRoute

    /**
     * 游戏 H5 承载页（游戏域自建，2026-11-14 架构修正）：携带游戏业务数据（简介 desc），
     * 与公共容器（app 壳 WebView 路由）分离——公共容器零业务字段。
     *
     * @param url 可玩地址（后端下发）
     * @param title 游戏名（页头标题）
     * @param desc 一句话玩法说明（服务端文案）；空串不显示简介入口
     */
    @Serializable
    @SerialName("GameWebView")
    data class GameWebView(
        val url: String = "",
        val title: String = "",
        val desc: String = "",
    ) : GameRoute
}
