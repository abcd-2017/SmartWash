package com.smartwash.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.network.api.OrderApi
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.order.OrderInfo

class OrderPagingSource(private val orderApi: OrderApi, private val status: String) :
    PagingSource<Int, OrderInfo>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, OrderInfo> {
        return try {
            val currentPage = params.key ?: 1
            val orderList =
                orderApi.getOrderList(status, currentPage).data ?: emptyList()
            val prevKey = if (currentPage == 1) null else currentPage - 1
            val nextKey = if (orderList.isEmpty()) null else currentPage + 1

            LoadResult.Page(
                data = orderList,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Log.e(AppConstant.APP_NAME, "OrderPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, OrderInfo>): Int? {
        return state.anchorPosition
    }
}
