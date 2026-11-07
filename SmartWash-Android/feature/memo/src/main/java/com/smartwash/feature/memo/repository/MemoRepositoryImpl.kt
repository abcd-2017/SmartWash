package com.smartwash.feature.memo.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.memo.network.MemoApi
import com.smartwash.feature.memo.network.MemoSaveRequest
import com.smartwash.feature.memo.network.MemoTagCountVo
import com.smartwash.feature.memo.network.MemoUpdateRequest
import com.smartwash.feature.memo.network.MemoVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 速记手账数据实现：薄封装 MemoApi，统一「data 为 null 即失败」语义
 * （业务失败已被 ResponseInterceptor 转译为 NetworkException 抛出，
 * 这里只兜住 code=200 但 data 缺失的分支），成功以 code=200 为准。
 * update/delete 后端 Result.ok(null)：data 不消费；tags 聚合 data 缺失按空列表兜底（不阻塞页面）。
 */
@Singleton
class MemoRepositoryImpl @Inject constructor(
    private val memoApi: MemoApi,
) : MemoRepository {

    override suspend fun createMemo(content: String, tags: List<String>): Long {
        return memoApi.createMemo(MemoSaveRequest(content = content, tags = tags)).data ?: run {
            AppLogger.warn(TAG, "createMemo: 新建记录响应 data 为空，按失败上抛")
            throw NetworkException("记录数据为空", R.string.error_network_fail)
        }
    }

    override suspend fun updateMemo(id: Long, content: String, tags: List<String>, pinned: Boolean) {
        // 成功以 code=200 为准（拦截器已把非 200 转译为异常），data（String）不消费
        memoApi.updateMemo(id, MemoUpdateRequest(content = content, tags = tags, pinned = pinned))
    }

    override suspend fun deleteMemo(id: Long) {
        // 成功以 code=200 为准（拦截器已把非 200 转译为异常），data（String）不消费
        memoApi.deleteMemo(id)
    }

    override suspend fun pageMemos(
        page: Int,
        size: Int,
        keyword: String?,
        tag: String?,
    ): PageData<MemoVo> {
        return memoApi.pageMemos(page = page, size = size, keyword = keyword, tag = tag).data ?: run {
            AppLogger.warn(TAG, "pageMemos: 记录列表响应 data 为空（page=$page），按失败上抛")
            throw NetworkException("记录列表为空", R.string.error_network_fail)
        }
    }

    override suspend fun listTags(): List<MemoTagCountVo> {
        // 聚合接口 data 缺失按空列表兜底：标签行不可用不应阻塞列表主链路
        return memoApi.listTags().data ?: run {
            AppLogger.warn(TAG, "listTags: 标签聚合响应 data 为空，按空列表兜底")
            emptyList()
        }
    }

    private companion object {
        const val TAG = "MemoRepository"
    }
}
