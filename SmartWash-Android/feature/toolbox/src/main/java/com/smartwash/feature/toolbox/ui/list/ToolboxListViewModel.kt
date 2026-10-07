package com.smartwash.feature.toolbox.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.toolbox.paging.ShortCodePagingSource
import com.smartwash.feature.toolbox.repository.ToolboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * 短链列表 ViewModel：分页流走 :common:utils 的 [pagingFlow]
 * （debounce + flatMapLatest + cachedIn），列表创建/续期/删除后的刷新
 * 由页面消费 CHANGED_KEY 信号后调 [refresh] 重建 Pager。
 */
@HiltViewModel
class ToolboxListViewModel @Inject constructor(
    private val repository: ToolboxRepository,
) : ViewModel() {

    private val _trigger = MutableStateFlow("")

    val pagingFlow = pagingFlow(_trigger) {
        ShortCodePagingSource(repository)
    }

    /** 外部信号驱动刷新（返回列表/下拉手势共用） */
    fun refresh() {
        _trigger.value = "refresh-${System.nanoTime()}"
    }
}
