package com.smartwash.feature.review.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 记忆复习类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class ReviewRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"ReviewQueue"/"ReviewStudy"/"ReviewMaterials"）；模块内页面跳转
 * 一律引用本类（feature 不得反向依赖 app 壳层的 PageConstant）。
 *
 * 参数形态：startCardId 带默认值 -1L → query（与迁移前注册形态
 * "ReviewStudy?startCardId={startCardId}" 一致）；属性名即 SavedStateHandle key。
 *
 * 页面经 app 壳 NavHost 一行 [com.smartwash.feature.review.ui.reviewGraph] 聚合，
 * 入口宿主后续接入（本次不改壳层导航）。
 */
sealed interface ReviewRoute : SmartWashRoute {

    /** 今日队列页（到期卡 + 新卡计数条与队列列表） */
    @Serializable
    @SerialName("ReviewQueue")
    data object Queue : ReviewRoute

    /** 复习页（翻面 + 四档评分；startCardId 指定从某张卡开始，缺省 -1 = 从队首） */
    @Serializable
    @SerialName("ReviewStudy")
    data class Study(val startCardId: Long = -1L) : ReviewRoute

    /** 资料页（资料列表 + 粘贴文本建资料 + 卡片列表） */
    @Serializable
    @SerialName("ReviewMaterials")
    data object Materials : ReviewRoute

    companion object {
        /** 复习页路由入参 key：起始卡片 id（[Study] 的 startCardId，SavedStateHandle 读取同源） */
        const val ARG_START_CARD_ID = "startCardId"

        /**
         * 数据变更信号 key（存于队列页 backStackEntry 的 savedStateHandle）：
         * 资料页建资料/建卡、复习页每次评分后置 true，队列页消费后复位并重拉队列
         * （复习评分改变到期状态，新建卡改变新卡配额，均影响今日队列）。
         */
        const val CHANGED_KEY = "review_data_changed"
    }
}
