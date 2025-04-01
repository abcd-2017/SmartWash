package com.smartwash.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.network.api.OrderApi
import com.smartwash.network.entity.order.OrderListFrom
import com.smartwash.network.vo.order.OrderInfo

class MyPagingSource(private val orderApi: OrderApi, private val status: String) :
    PagingSource<Int, OrderInfo>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, OrderInfo> {
        return try {
            val currentPage = params.key ?: 1
            Log.d("请求页面标记：", "请求第${currentPage}页")

            val orderList =
                orderApi.getOrderList(OrderListFrom(status, currentPage)).data ?: emptyList()
            val prevKey = if (currentPage == 1) null else currentPage - 1
            val nextKey = if (orderList.isEmpty()) null else currentPage + 1


            LoadResult.Page(
                data = orderList,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Log.d("测试错误数据", "-------${e.message}")
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, OrderInfo>): Int? {
        return state.anchorPosition
    }
}