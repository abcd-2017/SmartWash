package com.smartwash.feature.divination.ui.page.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.divination.core.DivMethod
import com.smartwash.feature.divination.database.DivRecordEntity
import com.smartwash.feature.divination.data.DivRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DivHistoryUiState(
    val filter: DivMethod? = null,
)

/**
 * 卦历案卷 VM：Room 卦历 + 术数筛选 chips。
 *
 * 服务端同步（issue #78，侵入最小口径）：列表仍以本地 Room 为单一数据源（卦盘复盘依赖
 * 本地 chartJson 原盘），进页经 [DivRecordRepository.syncServerLinks] 静默对账——拉
 * GET /records 分页首页，把「本地未关联服务端」的记录按 method+question+castAt 回填
 * serverRecordId（后续求解读/追问直接复用服务端卦例）；失败静默（卦盘/解读链路另有
 * ensureServerRecord 兜底建档），列表内容不受影响。
 */
@HiltViewModel
class DivHistoryViewModel @Inject constructor(
    private val repository: DivRecordRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow<DivMethod?>(null)
    val uiState: StateFlow<DivHistoryUiState> = _filter.map { DivHistoryUiState(filter = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DivHistoryUiState())

    val records: StateFlow<List<DivRecordEntity>> =
        combine(repository.observeAll(), _filter) { all, filter ->
            if (filter == null) all else all.filter { it.method == filter.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.syncServerLinks() }
    }

    fun onFilter(method: DivMethod?) {
        _filter.value = method
    }
}
