package com.smartwash.feature.lab

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 解压实验舱类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class LabRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"LabHome"/"LabBubbleWrap"/...）；模块内页面跳转一律引用本类
 * （feature 不得反向依赖 app 壳层的 PageConstant）。
 *
 * 当前无底栏入口，页面仅作为独立路由注册在宿主 NavHost，入口为
 * 深链 smartwash://lab（本模块 di/LabDeepLinkModule 贡献）与后端
 * workbench_entry 配置的工作台卡片（target 指向同一深链）。
 */
sealed interface LabRoute : SmartWashRoute {

    /** 实验舱首页（三个玩具入口卡） */
    @Serializable
    @SerialName("LabHome")
    data object Home : LabRoute

    /** 泡泡纸（Canvas 网格戳泡泡） */
    @Serializable
    @SerialName("LabBubbleWrap")
    data object BubbleWrap : LabRoute

    /** 呼吸引导（4-7-8 / Box 相位状态机 + 圆环动效） */
    @Serializable
    @SerialName("LabBreath")
    data object Breath : LabRoute

    /** 指尖鱼群（boids 模拟 + 指针交互） */
    @Serializable
    @SerialName("LabFish")
    data object Fish : LabRoute

    /** 落沙实验室（元胞自动机元素沙盒） */
    @Serializable
    @SerialName("LabSand")
    data object Sand : LabRoute

    /** 指尖流体（stable fluids + 触摸注入） */
    @Serializable
    @SerialName("LabFluid")
    data object Fluid : LabRoute

    /** 滚筒节拍机（8 音色 × 16 步音序器） */
    @Serializable
    @SerialName("LabDrum")
    data object Drum : LabRoute

    /** 今日一画（程序化水墨山水生成） */
    @Serializable
    @SerialName("LabPainting")
    data object Painting : LabRoute

    /** 声景混音器（程序化环境声叠混） */
    @Serializable
    @SerialName("LabSoundscape")
    data object Soundscape : LabRoute

    /** 泡泡精灵（页内电子宠物） */
    @Serializable
    @SerialName("LabPet")
    data object Pet : LabRoute

    /** 掌上星图（陀螺仪星空） */
    @Serializable
    @SerialName("LabStarMap")
    data object StarMap : LabRoute
}

/**
 * 实验舱玩具入口枚举（首页三张入口卡，顺序即展示序）。
 * [route] 指向 [LabRoute] 对应页面，卡片点击即导航过去。
 */
enum class LabToy(val route: LabRoute) {
    BUBBLE_WRAP(LabRoute.BubbleWrap),
    BREATH(LabRoute.Breath),
    FISH(LabRoute.Fish),
    SAND(LabRoute.Sand),
    FLUID(LabRoute.Fluid),
    DRUM(LabRoute.Drum),
    PAINTING(LabRoute.Painting),
    SOUNDSCAPE(LabRoute.Soundscape),
    PET(LabRoute.Pet),
    STAR_MAP(LabRoute.StarMap),
}
