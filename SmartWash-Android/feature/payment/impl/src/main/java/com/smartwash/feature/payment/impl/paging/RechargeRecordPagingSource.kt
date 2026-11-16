package com.smartwash.feature.payment.impl.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.payment.impl.network.api.RechargeApi
import com.smartwash.feature.payment.impl.network.vo.recharge.RechargeRecordVo

class RechargeRecordPagingSource(
    private val rechargeApi: RechargeApi,
) : PagingSource<Int, RechargeRecordVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RechargeRecordVo> {
        return try {
            val currentPage = params.key ?: 1
            val records = rechargeApi.getRechargeRecordList(currentPage).data?.records ?: emptyList()
            val prevKey = if (currentPage == 1) null else currentPage - 1
            val nextKey = if (records.isEmpty()) null else currentPage + 1

            LoadResult.Page(
                data = records,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "RechargeRecordPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, RechargeRecordVo>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    private companion object {
        const val TAG = "RechargeRecordPagingSource"
    }
}
