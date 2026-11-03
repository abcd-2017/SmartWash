package com.smartwash.feature.mood.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.mood.MoodConstant
import com.smartwash.feature.mood.MoodMonth
import com.smartwash.feature.mood.R
import com.smartwash.feature.mood.network.MoodLevelCountVo
import com.smartwash.feature.mood.network.MoodStatsVo
import com.smartwash.feature.mood.repository.MoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 月度趋势 ViewModel：等级分布（stats.distribution，缺的等级端上补 0）+ 记录天数 + 月均值。
 * ym（yyyy-MM）驱动，切月 collectLatest 重载。
 */
@HiltViewModel
class MoodTrendViewModel @Inject constructor(
    private val repository: MoodRepository,
) : ViewModel() {

    data class UiState(
        val ym: String = MoodMonth.current(),
        val loadState: RequestState = RequestState.Idle,
        val stats: MoodStatsVo? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    /** 手动重试触发器（切月由 ym collectLatest 自然重载） */
    private val _retry = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            _retry.collectLatest { load(_uiState.value.ym) }
        }
    }

    /** 切月（delta 正=往后，负=往前） */
    fun shiftMonth(delta: Int) {
        _uiState.update { it.copy(ym = MoodMonth.shift(it.ym, delta.toLong()), stats = null) }
    }

    /** 加载失败重试 */
    fun retry() {
        _retry.update { it + 1 }
    }

    /**
     * distribution 按等级升序补全 1..5（契约：只含出现过的等级，缺的端上补 0）。
     * 直接喂给页面静态条形，页面不再做二次拼装。
     */
    private fun load(ym: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadState = RequestState.Loading) }
            try {
                val vo = repository.monthStats(ym)
                val counts = vo.distribution.associate { it.level to it.recordCount }
                val full = (MoodConstant.LEVEL_MIN..MoodConstant.LEVEL_MAX).map { level ->
                    MoodLevelCountVo(
                        level = level,
                        recordCount = counts[level] ?: 0L,
                    )
                }
                _uiState.update {
                    it.copy(loadState = RequestState.Success, stats = vo.copy(distribution = full))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                _uiState.update { it.copy(loadState = RequestState.Error(e.resId, e.message)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loadState = RequestState.Error(R.string.mood_trend_error_load)) }
            }
        }
    }
}
