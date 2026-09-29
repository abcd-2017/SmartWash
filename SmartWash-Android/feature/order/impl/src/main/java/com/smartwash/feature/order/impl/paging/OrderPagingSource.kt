package com.smartwash.feature.order.impl.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.impl.OrderImplConstant
import com.smartwash.feature.order.impl.network.api.OrderServiceApi
import com.smartwash.feature.order.impl.network.vo.order.toModel

/**
 * 订单分页数据源（原 app 的 paging/OrderPagingSource，T6.1 迁入）。
 *
 * 经 order-api 的 OrderApi.orderPagingSource 工厂对外提供（消费方：取件页）。
 * 行为零改动：data 为 null 按空页处理（不抛错），保持原"静默结束分页"语义——
 * 与 Repository 的"null 抛 NetworkException"语义不同，故直调 ServiceApi 而非走 Repository。
 */
class OrderPagingSource(
    private val orderServiceApi: OrderServiceApi,
    private val status: String,
    private val pageSize: Int = OrderImplConstant.PAGE_SIZE,
) : PagingSource<Int, OrderInfo>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, OrderInfo> {
        return try {
            val currentPage = params.key ?: 1
            val orderList =
                orderServiceApi.getOrderList(status, currentPage, pageSize).data?.map { it.toModel() } ?: emptyList()
            val prevKey = if (currentPage == 1) null else currentPage - 1
            // 返回条数不足一页说明已是最后一页，避免整页恰好 10 条时多打一次空页
            val nextKey = if (orderList.size < pageSize) null else currentPage + 1

            LoadResult.Page(
                data = orderList,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Log.e(OrderImplConstant.APP_NAME, "OrderPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, OrderInfo>): Int? {
        return state.anchorPosition
    }
}
