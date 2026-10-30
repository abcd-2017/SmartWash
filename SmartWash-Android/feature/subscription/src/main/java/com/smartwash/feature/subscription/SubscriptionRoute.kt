package com.smartwash.feature.subscription

/**
 * 订阅域路由常量（模块内单一事实来源，模式同 ToolboxRoute/DivRoute）。
 *
 * 模块内页面跳转一律引用本常量；当前无任何宿主入口指向本路由，页面仅作为独立
 * 路由经 [com.smartwash.feature.subscription.ui.subscriptionGraph] 注册，
 * 宿主 NavHost 一行 subscriptionGraph 完成聚合（入口接入由后续任务执行）。
 */
sealed class SubscriptionRoute(val text: String) {

    /** 订阅列表 */
    data object List : SubscriptionRoute("SubscriptionList")

    /** 添加/编辑表单（query 携带 id，缺省 -1 = 新建，见 [ui.edit.SubscriptionEditViewModel]） */
    data object Edit : SubscriptionRoute("SubscriptionEdit")

    /** 订阅详情（query 携带 id，详情数据实时拉取保证状态/派生字段新鲜） */
    data object Detail : SubscriptionRoute("SubscriptionDetail")

    /** 提醒列表（未读/全部筛选，点击标记已读） */
    data object Reminders : SubscriptionRoute("SubscriptionReminders")

    companion object {
        /**
         * 变更信号 key（存于发起页上一栈位的 savedStateHandle）：
         * 新建/编辑/删除/状态流转/打卡/标记已读成功后置 true，
         * 列表与详情页消费后复位并重载，驱动分页流与派生字段刷新。
         */
        const val CHANGED_KEY = "subscription_changed"
    }
}
