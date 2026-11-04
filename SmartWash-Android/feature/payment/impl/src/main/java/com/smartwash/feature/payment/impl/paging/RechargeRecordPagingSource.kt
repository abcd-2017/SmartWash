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
        const val TAG = "RechargeRecordPagingSource"
    }
}
