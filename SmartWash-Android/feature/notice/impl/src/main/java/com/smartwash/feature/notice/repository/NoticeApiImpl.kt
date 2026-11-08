package com.smartwash.feature.notice.repository

import com.smartwash.feature.notice.api.NoticeApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NoticeApi] 的 @Binds 实现（模式同 coupon 的 CouponApiImpl）：
 * 跨模块消费方（user-impl 我的页）经接口取未读数，不触碰本域 Retrofit 接口与仓库细节。
 */
@Singleton
class NoticeApiImpl @Inject constructor(
    private val noticeRepository: NoticeRepository,
) : NoticeApi {

    override suspend fun getUnreadCount(): Long = noticeRepository.getUnreadCount()
}
