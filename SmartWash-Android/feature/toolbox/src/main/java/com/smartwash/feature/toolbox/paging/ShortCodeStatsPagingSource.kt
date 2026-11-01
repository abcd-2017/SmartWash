package com.smartwash.feature.toolbox.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.feature.toolbox.ToolboxConstant
import com.smartwash.feature.toolbox.network.ShortCodeStatsVo
import com.smartwash.feature.toolbox.repository.ToolboxRepository
import kotlinx.coroutines.CancellationException

/**
 * 短码访问明细分页源（/stats，IP 仅存摘要）。取消不作错误，同 [ShortCodePagingSource]。
 */
class ShortCodeStatsPagingSource(
    private val repository: ToolboxRepository,
    private val shortCodeId: Long,
) : PagingSource<Int, ShortCodeStatsVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ShortCodeStatsVo> {
        return try {
            val currentPage = params.key ?: 1
            val pageData = repository.pageShortCodeStats(
                id = shortCodeId,
                page = currentPage,
                size = ToolboxConstant.PAGE_SIZE,
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
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ShortCodeStatsVo>): Int? {
        // 2026-11-01 闪退修复：原直接返回 anchorPosition（位置索引），而本源的 key 是**页码**——
        // 刷新时拿位置当页码用，会重复加载已加载页（甚至算出负页），导致列表出现重复 id、
        // LazyColumn key 冲突崩溃（短链列表下拉刷新必现）。改为由锚点位置反推页码。
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
