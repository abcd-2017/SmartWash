package com.smartwash.feature.memo.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.memo.MemoConstant
import com.smartwash.feature.memo.network.MemoVo
import com.smartwash.feature.memo.repository.MemoRepository
import kotlinx.coroutines.CancellationException

/**
 * 速记手账记录分页源（模式对齐 ShortCodePagingSource）。
 * keyword/tag 透传后端过滤；置顶优先由后端排序，端上不重排。
 * 取消不作错误：CancellationException 先 rethrow（Compose 硬规则），其余异常转 LoadResult.Error。
 */
class MemoPagingSource(
    private val repository: MemoRepository,
    private val keyword: String?,
    private val tag: String?,
) : PagingSource<Int, MemoVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MemoVo> {
        return try {
            val currentPage = params.key ?: 1
            val pageData = repository.pageMemos(
                page = currentPage,
                size = MemoConstant.PAGE_SIZE,
                keyword = keyword?.takeIf { it.isNotBlank() },
                tag = tag?.takeIf { it.isNotBlank() },
            )
            val records = pageData.records
            LoadResult.Page(
                data = records,
                prevKey = if (currentPage == 1) null else currentPage - 1,
                nextKey = if (records.isEmpty()) null else currentPage + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "MemoPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MemoVo>): Int? {
        // 本源的 key 是页码：刷新时由锚点位置反推页码，禁止直接把 anchorPosition
        // 当页码用（会重复加载已加载页，LazyColumn key 冲突崩溃——见 toolbox 2026-11-01 修复）
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    private companion object {
        const val TAG = "MemoPagingSource"
    }
}
