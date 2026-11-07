package com.smartwash.feature.divination

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 观象台类型安全路由（模块内单一事实来源，T4.1 随整模块迁入）。
 *
 * 迁移前为 `sealed class DivRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"DivHome"/"DivAsk"/...），保证宿主 NavHost 注册与模块内跳转行为等价；
 * 模块内页面跳转一律引用本类（feature 不得反向依赖 app 壳层的 PageConstant）。
 *
 * 参数形态：recordId 无默认值 → path；method/question/category/animate 带默认值
 * → query（与迁移前注册形态 "DivChart/{recordId}?animate={animate}" 等一致）。
 */
sealed interface DivRoute : SmartWashRoute {

    /** 观象台首页 */
    @Serializable
    @SerialName("DivHome")
    data object Home : DivRoute

    /** 心中所问（method 缺省 liuyao） */
    @Serializable
    @SerialName("DivAsk")
    data class Ask(val method: String = "liuyao") : DivRoute

    /** 摇卦（question/category 缺省空/other） */
    @Serializable
    @SerialName("DivCast")
    data class Cast(
        val question: String = "",
        val category: String = "other",
    ) : DivRoute

    /** 卦盘（recordId path；animate 缺省 false） */
    @Serializable
    @SerialName("DivChart")
    data class Chart(
        val recordId: Long,
        val animate: Boolean = false,
    ) : DivRoute

    /** 解读（recordId path） */
    @Serializable
    @SerialName("DivReading")
    data class Reading(val recordId: Long) : DivRoute

    /** 继续追问（recordId path） */
    @Serializable
    @SerialName("DivFollowUp")
    data class FollowUp(val recordId: Long) : DivRoute

    /** 卦历案卷 */
    @Serializable
    @SerialName("DivHistory")
    data object History : DivRoute

    /** 卦例搜索（占卜闭环：关键词翻检案卷与解读） */
    @Serializable
    @SerialName("DivSearch")
    data object Search : DivRoute

    /** 待回访（占卜闭环：status=0 待回访卦例回访簿） */
    @Serializable
    @SerialName("DivNudge")
    data object Nudge : DivRoute

    /** 盘感（占卜闭环：卦例统计复盘） */
    @Serializable
    @SerialName("DivStats")
    data object Stats : DivRoute
}
