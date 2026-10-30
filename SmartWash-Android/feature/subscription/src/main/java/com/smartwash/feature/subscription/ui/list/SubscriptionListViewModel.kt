package com.smartwash.feature.subscription.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.subscription.SubscriptionStatus
import com.smartwash.feature.subscription.paging.SubscriptionPagingSource
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * 订阅列表 ViewModel：状态筛选 + 刷新 tick 合入分页触发流（pagingFlow 统一
 * debounce + flatMapLatest + cachedIn），新建/编辑/删除/状态流转/标记已读后的刷新
 * 由页面消费 SubscriptionRoute.CHANGED_KEY 信号后调 [refresh] 重建 Pager。
 */
@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    private val _statusFilter = MutableStateFlow<SubscriptionStatus?>(null)
    val statusFilter: StateFlow<SubscriptionStatus?> = _statusFilter.asStateFlow()

    private val refreshTick = MutableStateFlow("")

    val pagingFlow = pagingFlow(
        combine(_statusFilter, refreshTick) { status, tick -> "${status?.raw ?: FILTER_ALL}|$tick" }
    ) { tag ->
        SubscriptionPagingSource(
            repository = repository,
            status = tag.substringBefore("|").takeIf { it != FILTER_ALL },
        )
    }

    /** 状态筛选（null = 全部；相同筛选不重触发分页流） */
    fun setStatus(status: SubscriptionStatus?) {
        if (_statusFilter.value != status) _statusFilter.value = status
    }

    /** 外部信号驱动刷新（变更信号/下拉手势共用） */
    fun refresh() {
        refreshTick.value = "t-${System.nanoTime()}"
    }

    private companion object {
        const val FILTER_ALL = "all"
    }
}
