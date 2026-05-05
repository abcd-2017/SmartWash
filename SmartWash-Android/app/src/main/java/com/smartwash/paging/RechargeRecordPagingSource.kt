package com.smartwash.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.network.api.RechargeApi
import com.smartwash.network.vo.recharge.RechargeRecordVo
import com.smartwash.utils.AppConstant

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
            Log.e(AppConstant.APP_NAME, "RechargeRecordPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, RechargeRecordVo>): Int? {
        return state.anchorPosition
    }
}
