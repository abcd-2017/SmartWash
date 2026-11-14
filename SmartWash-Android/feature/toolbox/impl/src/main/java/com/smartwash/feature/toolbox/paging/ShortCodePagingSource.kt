package com.smartwash.feature.toolbox.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.toolbox.api.ToolboxConstant
import com.smartwash.feature.toolbox.api.network.ShortCodeVo
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
            if (records.isEmpty()) {
                // 空页留痕：分页热路径，debug 级别
                AppLogger.debug(TAG, "load: 短链列表空页，结束分页 page=$currentPage")
            }
            LoadResult.Page(
                data = records,
                prevKey = if (currentPage == 1) null else currentPage - 1,
                nextKey = if (records.isEmpty()) null else currentPage + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "ShortCodePagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ShortCodeVo>): Int? {
        // 2026-11-01 闪退修复：原直接返回 anchorPosition（位置索引），而本源的 key 是**页码**——
        // 刷新时拿位置当页码用，会重复加载已加载页（甚至算出负页），导致列表出现重复 id、
        // LazyColumn key 冲突崩溃（短链列表下拉刷新必现）。改为由锚点位置反推页码。
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    private companion object {
        const val TAG = "ShortCodePagingSource"
    }
}
