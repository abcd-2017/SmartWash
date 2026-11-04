package com.smartwash.feature.subscription.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.subscription.SubscriptionConstant
import com.smartwash.feature.subscription.network.SubscriptionVo
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException

/**
 * 订阅列表分页源（模式对齐 ShortCodePagingSource）。
 * 取消不作错误：CancellationException 先 rethrow（Compose 硬规则），其余异常转 LoadResult.Error。
 */
class SubscriptionPagingSource(
    private val repository: SubscriptionRepository,
    private val status: String?,
) : PagingSource<Int, SubscriptionVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SubscriptionVo> {
        return try {
            val currentPage = params.key ?: 1
            val pageData = repository.pageSubscriptions(
                page = currentPage,
                size = SubscriptionConstant.PAGE_SIZE,
                status = status,
            )
            val records = pageData.records
            if (records.isEmpty()) {
                // 空页留痕：分页热路径，debug 级别
                AppLogger.debug(TAG, "load: 订阅列表空页，结束分页 status=$status page=$currentPage")
            }
            LoadResult.Page(
                data = records,
                prevKey = if (currentPage == 1) null else currentPage - 1,
                nextKey = if (records.isEmpty()) null else currentPage + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "SubscriptionPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SubscriptionVo>): Int? {
        return state.anchorPosition
    }

    private companion object {
        const val TAG = "SubscriptionPagingSource"
    }
}
