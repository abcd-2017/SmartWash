package com.smartwash.feature.lab

/**
 * 解压实验舱路由常量（模块内单一事实来源，模式同 GameRoute/ToolboxRoute）。
 *
 * 模块内页面跳转一律引用本常量（feature 不得反向依赖 app 壳层的 PageConstant）；
 * 当前无底栏入口，页面仅作为独立路由注册在宿主 NavHost，入口为
 * 深链 smartwash://lab（本模块 di/LabDeepLinkModule 贡献）与后端
 * workbench_entry 配置的工作台卡片（target 指向同一深链）。
 */
sealed class LabRoute(val text: String) {

    /** 实验舱首页（三个玩具入口卡） */
    data object Home : LabRoute("LabHome")

    /** 泡泡纸（Canvas 网格戳泡泡） */
    data object BubbleWrap : LabRoute("LabBubbleWrap")

    /** 呼吸引导（4-7-8 / Box 相位状态机 + 圆环动效） */
    data object Breath : LabRoute("LabBreath")

    /** 指尖鱼群（boids 模拟 + 指针交互） */
    data object Fish : LabRoute("LabFish")
}

/**
 * 实验舱玩具入口枚举（首页三张入口卡，顺序即展示序）。
 * [route] 指向 [LabRoute] 对应页面，卡片点击即导航过去。
 */
enum class LabToy(val route: String) {
    BUBBLE_WRAP(LabRoute.BubbleWrap.text),
    BREATH(LabRoute.Breath.text),
    FISH(LabRoute.Fish.text),
}
