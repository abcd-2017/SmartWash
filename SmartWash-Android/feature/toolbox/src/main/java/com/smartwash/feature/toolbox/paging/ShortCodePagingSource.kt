package com.smartwash.feature.toolbox.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.feature.toolbox.ToolboxConstant
import com.smartwash.feature.toolbox.network.ShortCodeVo
import com.smartwash.feature.toolbox.repository.ToolboxRepository
import kotlinx.coroutines.CancellationException

/**
 * 我的短码分页源（模式对齐 RechargeRecordPagingSource）。
 * 取消不作错误：CancellationException 先 rethrow（Compose 硬规则），其余异常转 LoadResult.Error。
 */
class ShortCodePagingSource(
    private val repository: ToolboxRepository,
) : PagingSource<Int, ShortCodeVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ShortCodeVo> {
        return try {
            val currentPage = params.key ?: 1
            val pageData = repository.pageMyShortCodes(
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

    override fun getRefreshKey(state: PagingState<Int, ShortCodeVo>): Int? {
        return state.anchorPosition
    }
}
