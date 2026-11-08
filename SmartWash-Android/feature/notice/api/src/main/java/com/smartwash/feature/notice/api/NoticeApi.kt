package com.smartwash.feature.notice.api

/**
 * 站内信域对外服务契约（跨模块数据消费方持有，命名对齐 CouponApi/OrderApi 先例）。
 *
 * 消费方：user-impl 的我的页（UserInfoViewModel 注入本接口取未读角标，
 * 不触碰 :feature:notice:impl 的 NoticeRepository 实现细节）；
 * 实现为 notice 域的 NoticeApiImpl（Hilt @Binds，di/NoticeModule），
 * 依赖方向 user-impl → notice:api（铁律 2：feature 间仅 impl → 他人 api）。
 *
 * 类型面收窄：仅暴露未读数（Long），不泄漏 NoticeVo 全量字段。
 */
interface NoticeApi {

    /** 未读数（公告总数 − 当前用户已读数；后端异常响应 data 缺失时按 0 处理，角标隐藏） */
    suspend fun getUnreadCount(): Long
}
