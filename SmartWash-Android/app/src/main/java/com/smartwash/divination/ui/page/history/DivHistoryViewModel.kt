package com.smartwash.divination.ui.page.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.divination.core.DivMethod
import com.smartwash.divination.data.DivRecordEntity
import com.smartwash.divination.data.DivRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DivHistoryUiState(
    val filter: DivMethod? = null,
)

/**
 * 卦历案卷 VM：Room 卦历 + 术数筛选 chips。
 */
@HiltViewModel
class DivHistoryViewModel @Inject constructor(
    repository: DivRecordRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow<DivMethod?>(null)
    val uiState: StateFlow<DivHistoryUiState> = _filter.map { DivHistoryUiState(filter = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DivHistoryUiState())

    val records: StateFlow<List<DivRecordEntity>> =
        combine(repository.observeAll(), _filter) { all, filter ->
            if (filter == null) all else all.filter { it.method == filter.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onFilter(method: DivMethod?) {
        _filter.value = method
    }
}
