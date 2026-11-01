package com.smartwash.feature.laundry.api

/**
 * 洗衣域路由常量（模块内单一事实来源，模式仿 PaymentRoute / OrderRoute / DivRoute）。
 *
 * 路由值对齐 app 壳层 PageConstant 的现有常量（PageConstant 侧已改为委托本类，
 * 保证宿主 NavHost 注册与模块内跳转永不漂移）；feature 不得反向依赖 app 壳层
 * 的 PageConstant。
 *
 * 消费方（均为导航诉求）：
 * - app 壳：NavHost 注册（PageConstant 委托）+ 首页服务网格「预约洗衣」跳转；
 * - laundry：域内页面（服务 tab 页 / 洗衣预约页）。
 */
sealed class LaundryRoute(val text: String) {
    /**
     * 洗衣预约页（首页服务网格「预约洗衣」跳入，选套餐下单）。
     *
     * [ARG_ITEM_ID] 可选参数：由服务页套餐行点击跳入时指定默认选中的套餐（-1 = 不指定）。
     * 消费方一律用 [Laundry.build] 拼最终路由，禁止直接 navigate 到带 `{itemId}` 占位符的 [text]。
     */
    object Laundry : LaundryRoute("Laundry?itemId={itemId}") {
        const val ARG_ITEM_ID = "itemId"

        /** 拼路由：[itemId] 传 -1 表示不指定默认套餐（首页网格 / 深链等入口） */
        fun build(itemId: Long): String = "Laundry?itemId=$itemId"
    }

    /** 服务页（主页底部 tab「服务」，洗衣服务项目列表） */
    data object Service : LaundryRoute("Service")
}
