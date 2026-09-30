package com.smartwash.feature.divination

/**
 * 观象台路由常量（模块内单一事实来源，T4.1 随整模块迁入）。
 *
 * 模块内页面跳转一律引用本常量（feature 不得反向依赖 app 壳层的 PageConstant）；
 * app 的 `PageConstant.Div*` 委托本常量的值，宿主 NavHost 注册与模块内跳转永不漂移。
 * T8.1 统一拆路由后由本模块直接对外暴露，app 侧委托常量随之移除。
 */
sealed class DivRoute(val text: String) {
    data object Home : DivRoute("DivHome")
    data object Ask : DivRoute("DivAsk")
    data object Cast : DivRoute("DivCast")
    data object Chart : DivRoute("DivChart")
    data object Reading : DivRoute("DivReading")
    data object FollowUp : DivRoute("DivFollowUp")
    data object History : DivRoute("DivHistory")
}
