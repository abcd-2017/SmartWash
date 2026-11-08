package com.smartwash.feature.notice.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.notice.api.NoticeServiceApi
import com.smartwash.feature.notice.api.model.NoticeVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 站内信仓库：站内信网络链路的唯一出口（ViewModel 不直接持有 API，MVVM 硬规则）。
 *
 * data 为 null 统一按失败处理上抛 [NetworkException]（对齐 coupon/points 仓库模式）——
 * code=200 信封但 data 缺失是异常响应，让上层能区分「无数据」与「请求失败」
 * （ResponseInterceptor 不拦此场景）。唯一例外是未读数：次要点缀数据，data 缺失按 0
 * 处理（角标隐藏），不让徽章请求失败影响我的页主流程。
 *
 * 已读幂等由后端保证（重复标记/并发撞唯一索引均静默成功），端上不做去重请求。
 */
@Singleton
class NoticeRepository @Inject constructor(
    private val noticeServiceApi: NoticeServiceApi,
) {

    /** 我的收件箱分页（page 从 1 起，size 上限 100，见后端 BaseSearchFrom） */
    suspend fun getInbox(page: Int, size: Int): PageData<NoticeVo> =
        noticeServiceApi.inbox(page, size).data
            ?: run {
                AppLogger.warn(TAG, "getInbox 响应 data 为空，按失败处理上抛（page=$page, size=$size）")
                throw NetworkException("收件箱数据为空", R.string.error_network_fail)
            }

    /**
     * 未读数（公告总数 − 当前用户已读数）。
     * data 缺失按 0 处理（角标隐藏），仅留痕不上抛——未读数是点缀数据，
     * 失败不该让我的页/收件箱整页进错误态。
     */
    suspend fun getUnreadCount(): Long =
        noticeServiceApi.unreadCount().data
            ?: run {
                AppLogger.warn(TAG, "getUnreadCount 响应 data 为空，按 0 处理（角标隐藏）")
                0L
            }

    /** 标记已读（后端幂等；data 恒为 null，不读 data） */
    suspend fun markRead(id: Long) {
        noticeServiceApi.markRead(id)
    }

    /** 全部已读（后端幂等；data 缺失按 0 处理） */
    suspend fun readAll() {
        noticeServiceApi.readAll()
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "NoticeRepository"
    }
}
