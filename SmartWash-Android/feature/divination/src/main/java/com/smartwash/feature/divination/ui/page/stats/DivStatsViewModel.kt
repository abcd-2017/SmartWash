package com.smartwash.feature.divination.ui.page.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.data.DivLoopRepository
import com.smartwash.feature.divination.network.DivStatsVo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 盘感 VM（占卜闭环三）：拉取卦例统计（总数/术数分布/类别分布/月度趋势/反馈应验率）。
 * 统计为服务端在线聚合，不落本地库；失败呈错误态供重试。
 */
@HiltViewModel
class DivStatsViewModel @Inject constructor(
    private val repository: DivLoopRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow<DivStatsVo?>(null)
    val stats: StateFlow<DivStatsVo?> = _stats.asStateFlow()

    private val _state = MutableStateFlow<RequestState>(RequestState.Idle)
    val state: StateFlow<RequestState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_state.value is RequestState.Loading) return
        _state.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val stats = repository.stats()
                if (stats != null) {
                    _stats.value = stats
                    _state.value = RequestState.Success
                } else {
                    _state.value = RequestState.Error(messageResId = R.string.div_error_network)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = RequestState.Error(
                    messageResId = R.string.div_error_network,
                    message = e.message,
                )
            }
        }
    }
}
