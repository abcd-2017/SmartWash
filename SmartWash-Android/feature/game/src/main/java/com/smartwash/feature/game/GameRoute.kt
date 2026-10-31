package com.smartwash.feature.game

/**
 * 游戏域路由常量（模块内单一事实来源，模式同 ToolboxRoute/DivRoute）。
 *
 * 模块内页面跳转一律引用本常量（feature 不得反向依赖 app 壳层的 PageConstant）；
 * 当前无底栏入口，页面仅作为独立路由注册在宿主 NavHost，入口为
 * 深链 smartwash://game-center（本模块 di/DeepLinkModule 贡献）与后端
 * workbench_entry 配置的工作台卡片（target 指向同一深链）。
 */
sealed class GameRoute(val text: String) {

    /** 游戏中心列表页 */
    data object Center : GameRoute("GameCenter")
}
