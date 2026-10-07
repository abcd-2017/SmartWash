package com.smartwash.feature.toolbox.repository

import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.toolbox.network.AddShortCodeRequest
import com.smartwash.feature.toolbox.network.ShortCodeStatsVo
import com.smartwash.feature.toolbox.network.ShortCodeVo
import com.smartwash.feature.toolbox.network.UpdateShortCodeRequest

/**
 * 短链数据契约（接口抽象，ViewModel/PagingSource 面向接口消费，
 * JVM 单测以 Fake 替换；实现见 [ToolboxRepositoryImpl]）。
 * data 为 null 一律按失败抛 [NetworkException]，让上层能区分「无数据」与「请求失败」。
 */
interface ToolboxRepository {

    suspend fun createShortCode(request: AddShortCodeRequest): ShortCodeVo

    suspend fun updateShortCode(id: Long, request: UpdateShortCodeRequest): ShortCodeVo

    suspend fun deleteShortCode(id: Long)

    suspend fun resolveShortCode(code: String): ShortCodeVo

    suspend fun pageMyShortCodes(page: Int, size: Int): PageData<ShortCodeVo>

    suspend fun pageShortCodeStats(id: Long, page: Int, size: Int): PageData<ShortCodeStatsVo>
}
