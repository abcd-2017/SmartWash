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
        return state.anchorPosition
    }
}
