package com.smartwash.feature.user.impl.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.user.impl.repository.UserRepository
import com.smartwash.feature.user.impl.network.vo.user.TransactionVo
import kotlinx.coroutines.CancellationException

/**
 * 钱包流水分页源（参照 :feature:payment 的 RechargeRecordPagingSource 范式，
 * 数据经 UserRepository 下发，VM 不直连 Api）。
 */
class TransactionPagingSource(
    private val userRepository: UserRepository,
) : PagingSource<Int, TransactionVo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionVo> {
        return try {
            val currentPage = params.key ?: 1
            val records = userRepository.getTransactions(currentPage, PAGE_SIZE).records
            val prevKey = if (currentPage == 1) null else currentPage - 1
            val nextKey = if (records.isEmpty()) null else currentPage + 1

            LoadResult.Page(
                data = records,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: CancellationException) {
            // 协程取消原样 rethrow，不能吞成 LoadResult.Error
            throw e
        } catch (e: Exception) {
            AppLogger.error(TAG, "TransactionPagingSource.load: ${e.message}", e)
            LoadResult.Error(throwable = e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TransactionVo>): Int? {
        // key 是页码：由锚点位置反推页码（对齐 RechargeRecordPagingSource 2026-11-01 闪退修复，
        // 禁止拿位置索引当页码用，否则重复加载已加载页导致 LazyColumn key 冲突）
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    companion object {
        /** 每页条数（后端 pageSize 上限 50，取 20 平衡流量与翻页频率） */
        const val PAGE_SIZE = 20

        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        private const val TAG = "TransactionPagingSource"
    }
}
