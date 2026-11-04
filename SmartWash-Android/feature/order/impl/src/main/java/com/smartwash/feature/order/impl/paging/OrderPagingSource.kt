package com.smartwash.feature.order.impl.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.AppLogger
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
            val data = orderServiceApi.getOrderList(status, currentPage, pageSize).data
            if (data == null) {
                // 空态降级留痕：与 Repository"null 抛异常"不同，本源静默结束分页，debug 级别（分页热路径）
                AppLogger.debug(TAG, "load: 订单列表数据为空，按空页结束分页 status=$status page=$currentPage")
            }
            val orderList = data?.map { it.toModel() } ?: emptyList()
            val prevKey = if (currentPage == 1) null else currentPage - 1
            // 返回条数不足一页说明已是最后一页，避免整页恰好 10 条时多打一次空页
            val nextKey = if (orderList.size < pageSize) null else currentPage + 1

            LoadResult.Page(
                data = orderList,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "OrderPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, OrderInfo>): Int? {
        // 2026-11-01 闪退修复：原直接返回 anchorPosition（位置索引），而本源的 key 是**页码**——
        // 刷新时拿位置当页码用，会重复加载已加载页（甚至算出负页），导致列表出现重复 id、
        // LazyColumn key 冲突崩溃（短链列表下拉刷新必现）。改为由锚点位置反推页码。
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "OrderPagingSource"
    }
}
