package com.smartwash.feature.divination.ui.page.nudge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.data.DivLoopRepository
import com.smartwash.feature.divination.network.DivNudgeVo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 待回访 VM（占卜闭环二）：status=0 待回访卦例回访簿。
 *
 * 分页策略：首页 20 条滚动触底续拉（total 由信封分页承载）；忽略为乐观移除
 * ——接口成功才从列表剔除并同步扣减 total（失败静默保留，下次进入可再操作）。
 */
@HiltViewModel
class DivNudgeViewModel @Inject constructor(
    private val repository: DivLoopRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<DivNudgeVo>>(emptyList())
    val items: StateFlow<List<DivNudgeVo>> = _items.asStateFlow()

    private val _state = MutableStateFlow<RequestState>(RequestState.Idle)
    val state: StateFlow<RequestState> = _state.asStateFlow()

    /** 忽略提交中的 recordId 集（按行防重复点击，不影响整页状态） */
    private val _ignoring = MutableStateFlow<Set<Long>>(emptySet())
    val ignoring: StateFlow<Set<Long>> = _ignoring.asStateFlow()

    private var page = 1
    private var total = 0L
    private var loading = false

    init {
        reload()
    }

    /** 刷新回访簿（下拉语义备用：回到第 1 页重拉） */
    fun reload() {
        page = 1
        load(reset = true)
    }

    /** 滚动触底续拉下一页（loading 中 / 已拉满则忽略） */
    fun loadMore() {
        if (loading || total <= _items.value.size) return
        page += 1
        load(reset = false)
    }

    /** 忽略一条待回访（成功后从列表移除并同步扣减 total） */
    fun ignore(recordId: Long) {
        if (recordId in _ignoring.value) return
        viewModelScope.launch {
            _ignoring.value = _ignoring.value + recordId
            try {
                if (repository.ignoreNudge(recordId)) {
                    _items.value = _items.value.filterNot { it.recordId == recordId }
                    total = (total - 1).coerceAtLeast(0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 忽略失败静默：该条保留在列表，用户可重试或下次进入再操作
            } finally {
                _ignoring.value = _ignoring.value - recordId
            }
        }
    }

    private fun load(reset: Boolean) {
        if (loading) return
        loading = true
        if (reset) _state.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val result = repository.nudges(page = page)
                if (result.code == 200) {
                    val records = result.data?.records.orEmpty()
                    total = result.data?.total ?: records.size.toLong()
                    _items.value = if (reset) records else _items.value + records
                    _state.value = RequestState.Success
                } else {
                    _state.value = RequestState.Error(
                        messageResId = R.string.div_error_network,
                        message = result.message,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = RequestState.Error(
                    messageResId = R.string.div_error_network,
                    message = e.message,
                )
            } finally {
                loading = false
            }
        }
    }
}
