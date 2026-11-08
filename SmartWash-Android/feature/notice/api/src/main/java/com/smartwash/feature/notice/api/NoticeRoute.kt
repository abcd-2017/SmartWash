package com.smartwash.feature.notice.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 站内信域类型安全路由（模块内单一事实来源，模式对齐 CouponRoute/PointsRoute）。
 *
 * 页面取舍：收件箱单页——后端 GET /web/auth/notices 一次返回全量公告分页（每条带当前用户
 * isRead），无「消息详情页」诉求（正文即列表摘要，点击只标记已读 + 按需深链跳转），故不拆详情路由。
 *
 * 消费方：
 * - app 壳：MainActivity NavHost 经 [com.smartwash.feature.notice.ui.noticeGraph] 注册；
 * - notice-impl：深链 host "messages" 贡献者落点；
 * - user-impl：我的页「消息中心」入口跨域跳转。
 */
sealed interface NoticeRoute : SmartWashRoute {

    /** 收件箱页（路由串 "Messages"，深链 host "messages" 的落点） */
    @Serializable
    @SerialName("Messages")
    data object Inbox : NoticeRoute
}
