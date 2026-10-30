package com.smartwash.feature.subscription.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.feature.subscription.SubscriptionConstant
import com.smartwash.feature.subscription.network.UsageLogVo
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException

/**
 * 使用打卡流水分页源（按使用日期倒序，模式对齐 SubscriptionPagingSource）。
 * 撤销打卡/新增打卡后由详情页置刷新信号重建 Pager。
 */
class SubscriptionUsagePagingSource(
    private val repository: SubscriptionRepository,
    private val subscriptionId: Long,
) : PagingSource<Int, UsageLogVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UsageLogVo> {
        return try {
            val currentPage = params.key ?: 1
            val pageData = repository.pageUsages(
                id = subscriptionId,
                page = currentPage,
                size = SubscriptionConstant.PAGE_SIZE,
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

    override fun getRefreshKey(state: PagingState<Int, UsageLogVo>): Int? {
        return state.anchorPosition
    }
}
