package com.smartwash.feature.toolbox

/**
 * 工具箱路由常量（模块内单一事实来源，模式同 DivRoute）。
 *
 * 模块内页面跳转一律引用本常量（feature 不得反向依赖 app 壳层的 PageConstant）；
 * 当前无任何入口指向本路由（工作台 tab 未改造），页面仅作为独立路由注册在
 * 宿主 NavHost，接入方式见 SmartWash-Android/docs/短链工具箱对接文档.md。
 */
sealed class ToolboxRoute(val text: String) {
    /** 我的短链列表 */
    data object List : ToolboxRoute("ToolboxShortCodeList")

    /** 新建短链 */
    data object Create : ToolboxRoute("ToolboxShortCodeCreate")

    /** 短链详情（参数经 query 携带，见 [ToolboxGraph]） */
    data object Detail : ToolboxRoute("ToolboxShortCodeDetail")

    companion object {
        /**
         * 列表页刷新信号 key（存于列表页 backStackEntry 的 savedStateHandle）：
         * 创建/续期/删除成功后由发起页置 true，列表页消费后复位，驱动 pagingFlow 重新加载。
         */
        const val CHANGED_KEY = "toolbox_short_code_changed"
    }
}
