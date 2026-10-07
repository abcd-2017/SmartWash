package com.smartwash.feature.toolbox.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.toolbox.network.AddShortCodeRequest
import com.smartwash.feature.toolbox.network.ShortCodeStatsVo
import com.smartwash.feature.toolbox.network.ShortCodeVo
import com.smartwash.feature.toolbox.network.ToolboxApi
import com.smartwash.feature.toolbox.network.UpdateShortCodeRequest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 短链数据实现：薄封装 ToolboxApi，统一「data 为 null 即失败」语义
 * （业务失败已被 ResponseInterceptor 转译为 NetworkException 抛出，
 * 这里只兜住 code=200 但 data 缺失的分支），成功以 code=200 为准。
 */
@Singleton
class ToolboxRepositoryImpl @Inject constructor(
    private val toolboxApi: ToolboxApi,
) : ToolboxRepository {

    override suspend fun createShortCode(request: AddShortCodeRequest): ShortCodeVo {
        return toolboxApi.createShortCode(request).data
            ?: throw NetworkException("短链数据为空", R.string.error_network_fail)
    }

    override suspend fun updateShortCode(id: Long, request: UpdateShortCodeRequest): ShortCodeVo {
        return toolboxApi.updateShortCode(id, request).data
            ?: throw NetworkException("短链数据为空", R.string.error_network_fail)
    }

    override suspend fun deleteShortCode(id: Long) {
        // 后端 Result.ok(null)：成功以 code=200 为准（拦截器已把非 200 转译为异常），data 不消费
        toolboxApi.deleteShortCode(id)
    }

    override suspend fun resolveShortCode(code: String): ShortCodeVo {
        return toolboxApi.resolveShortCode(code).data
            ?: throw NetworkException("短链数据为空", R.string.error_network_fail)
    }

    override suspend fun pageMyShortCodes(page: Int, size: Int): PageData<ShortCodeVo> {
        return toolboxApi.pageShortCodes(page = page, size = size).data
            ?: throw NetworkException("短链列表为空", R.string.error_network_fail)
    }

    override suspend fun pageShortCodeStats(id: Long, page: Int, size: Int): PageData<ShortCodeStatsVo> {
        return toolboxApi.pageShortCodeStats(id = id, page = page, size = size).data
            ?: throw NetworkException("访问明细为空", R.string.error_network_fail)
    }
}
