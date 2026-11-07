package com.smartwash.feature.readlater.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.readlater.ReadLaterConstant
import com.smartwash.feature.readlater.network.ReadLaterVo
import com.smartwash.feature.readlater.repository.ReadLaterRepository
import kotlinx.coroutines.CancellationException

/**
 * 稍后读分页源（模式对齐 ShortCodePagingSource；筛选参数由 ViewModel 组装传入）。
 * 取消不作错误：CancellationException 先 rethrow（Compose 硬规则），其余异常转 LoadResult.Error。
 */
class ReadLaterPagingSource(
    private val repository: ReadLaterRepository,
    private val status: Int?,
    private val keyword: String?,
    private val tag: String?,
) : PagingSource<Int, ReadLaterVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ReadLaterVo> {
        return try {
            val currentPage = params.key ?: 1
            val pageData = repository.page(
                page = currentPage,
                size = ReadLaterConstant.PAGE_SIZE,
                status = status,
                tag = tag,
                keyword = keyword,
            )
            val records = pageData.records
            if (records.isEmpty()) {
                // 空页留痕：分页热路径，debug 级别
                AppLogger.debug(TAG, "load: 稍后读列表空页，结束分页 page=$currentPage")
            }
            LoadResult.Page(
                data = records,
                prevKey = if (currentPage == 1) null else currentPage - 1,
                nextKey = if (records.isEmpty()) null else currentPage + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "ReadLaterPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ReadLaterVo>): Int? {
        // key 是页码（对齐 2026-11-01 ShortCodePagingSource 闪退修复）：由锚点位置反推页码，
        // 禁止直接把 anchorPosition（位置索引）当页码用
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    private companion object {
        const val TAG = "ReadLaterPagingSource"
    }
}
