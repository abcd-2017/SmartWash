package com.smartwash.feature.subscription.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 订阅域类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class SubscriptionRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"SubscriptionList"/"SubscriptionEdit"/...）；模块内页面跳转一律引用
 * 本类。
 *
 * 参数形态：id 带默认值 -1L → query（缺省 -1 = 新建；属性名即 SavedStateHandle key，
 * 与 ui/edit/SubscriptionEditViewModel、ui/detail/SubscriptionDetailViewModel 的 ARG_ID 对齐）。
 *
 * 当前无任何宿主入口指向本路由，页面仅作为独立路由经
 * [com.smartwash.feature.subscription.ui.subscriptionGraph] 注册。
 */
sealed interface SubscriptionRoute : SmartWashRoute {

    /** 订阅列表 */
    @Serializable
    @SerialName("SubscriptionList")
    data object List : SubscriptionRoute

    /** 添加/编辑表单（id 缺省 -1 = 新建） */
    @Serializable
    @SerialName("SubscriptionEdit")
    data class Edit(val id: Long = -1L) : SubscriptionRoute

    /** 订阅详情（id；详情数据实时拉取保证状态/派生字段新鲜） */
    @Serializable
    @SerialName("SubscriptionDetail")
    data class Detail(val id: Long = -1L) : SubscriptionRoute

    /** 提醒列表（未读/全部筛选，点击标记已读） */
    @Serializable
    @SerialName("SubscriptionReminders")
    data object Reminders : SubscriptionRoute

    companion object {
        /**
         * 变更信号 key（存于发起页上一栈位的 savedStateHandle）：
         * 新建/编辑/删除/状态流转/打卡/标记已读成功后置 true，
         * 列表与详情页消费后复位并重载，驱动分页流与派生字段刷新。
         */
        const val CHANGED_KEY = "subscription_changed"
    }
}
