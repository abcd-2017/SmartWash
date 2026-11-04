package com.smartwash.feature.mood.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.mood.MoodMonth
import com.smartwash.feature.mood.R
import com.smartwash.feature.mood.network.MoodCalendarDayVo
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
 * 月历热图 ViewModel：ym（yyyy-MM）驱动的单数据源——切月 collectLatest 重载，
 * 加载中切月自动取消上一个请求。dayKey（date 字符串）→ 记录的 Map 供 Canvas 查表着色
 * （star_book 模式）。
 */
@HiltViewModel
class MoodCalendarViewModel @Inject constructor(
    private val repository: MoodRepository,
) : ViewModel() {

    data class UiState(
        val ym: String = MoodMonth.current(),
        val loadState: RequestState = RequestState.Idle,
        /** dayKey("yyyy-MM-dd") → 当日记录（无记录日期端上缺省灰格） */
        val records: Map<String, MoodCalendarDayVo> = emptyMap(),
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

    /** 切月（delta 正=往后，负=往前）；跨月/未来月/空月接口返回空数组，端上灰格 */
    fun shiftMonth(delta: Int) {
        _uiState.update { it.copy(ym = MoodMonth.shift(it.ym, delta.toLong()), records = emptyMap()) }
    }

    /** 加载失败重试 */
    fun retry() {
        _retry.update { it + 1 }
    }

    private suspend fun load(ym: String) {
        _uiState.update { it.copy(loadState = RequestState.Loading) }
        try {
            val days = repository.monthCalendar(ym)
            _uiState.update { it.copy(loadState = RequestState.Success, records = days.associateBy { day -> day.date }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            AppLogger.error(TAG, "MoodCalendarViewModel.load: ${e.message}", e)
            _uiState.update { it.copy(loadState = RequestState.Error(e.resId, e.message)) }
        } catch (e: Exception) {
            AppLogger.error(TAG, "MoodCalendarViewModel.load: ${e.message}", e)
            _uiState.update { it.copy(loadState = RequestState.Error(R.string.mood_error_load)) }
        }
    }

    private companion object {
        const val TAG = "MoodCalendarViewModel"
    }
}
