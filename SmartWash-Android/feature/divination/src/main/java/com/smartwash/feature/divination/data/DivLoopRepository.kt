package com.smartwash.feature.divination.data

import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.divination.network.DivinationApi
import com.smartwash.feature.divination.network.DivNudgeVo
import com.smartwash.feature.divination.network.DivRecordSearchVo
import com.smartwash.feature.divination.network.DivStatsVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 占卜闭环仓库 —— 卦例搜索 / 待回访 / 盘感三件套的服务端数据策略。
 *
 * 与卦历（DivRecordRepository，Room 单一数据源）不同：闭环三件套全部为
 * 服务端在线数据，不落本地库；VM 不直连 Retrofit，统一经本仓库访问
 * （MVVM 分层规则）。搜索关键词防抖由 VM 层承载，仓库只做单次请求。
 */
@Singleton
class DivLoopRepository @Inject constructor(
    private val api: DivinationApi,
) {

    /** 卦例关键词搜索（单次请求，分页参数透传） */
    suspend fun searchRecords(keyword: String, page: Int = 1, size: Int = 20): ApiResult<PageData<DivRecordSearchVo>> =
        api.searchRecordsByKeyword(keyword = keyword, page = page, size = size)

    /** 待回访列表（status=0，分页透传） */
    suspend fun nudges(page: Int, size: Int = 20): ApiResult<PageData<DivNudgeVo>> =
        api.getNudges(page = page, size = size)

    /**
     * 待回访计数（卦历页入口徽标）。失败静默回 0（徽标不亮，不打扰主流程）。
     */
    suspend fun nudgeCount(): Int = runCatching { api.getNudgeCount() }
        .onFailure { AppLogger.warn(TAG, "待回访计数拉取失败: ${it.message}") }
        .getOrNull()?.takeIf { it.code == 200 }?.data ?: 0

    /**
     * 忽略待回访（{id} = 待回访卦例 recordId）。
     * @return 业务是否成功（false 时列表保留该条，下次进入可再操作）
     */
    suspend fun ignoreNudge(recordId: Long): Boolean = runCatching { api.ignoreNudge(recordId) }
        .onFailure { AppLogger.warn(TAG, "忽略待回访失败 recordId=$recordId: ${it.message}") }
        .getOrNull()?.code == 200

    /** 盘感统计（失败静默回 null，页面呈现错误态可重试） */
    suspend fun stats(): DivStatsVo? = runCatching { api.getStats() }
        .getOrNull()?.takeIf { it.code == 200 }?.data

    private companion object {
        const val TAG = "DivLoopRepository"
    }
}
