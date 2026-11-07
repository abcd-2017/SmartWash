package com.smartwash.feature.divination.ui.page.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.divination.R
import com.smartwash.feature.divination.data.DivLoopRepository
import com.smartwash.feature.divination.network.DivRecordSearchVo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 卦例搜索 VM（占卜闭环一）：关键词防抖 300ms 后搜服务端案卷与解读全文。
 *
 * collectLatest 保证连输时取消上一轮未完成的请求（取消异常先 rethrow，
 * 不被误当成网络错误）；首页 size=20 一次拉足（搜索结果按相关性排序，
 * 不做滚动分页，翻页挂账闭环后续迭代）。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DivSearchViewModel @Inject constructor(
    private val repository: DivLoopRepository,
) : ViewModel() {

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private val _results = MutableStateFlow<List<DivRecordSearchVo>>(emptyList())
    val results: StateFlow<List<DivRecordSearchVo>> = _results.asStateFlow()

    private val _state = MutableStateFlow<RequestState>(RequestState.Idle)
    val state: StateFlow<RequestState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _keyword
                .debounce(300)
                .collectLatest { kw ->
                    val trimmed = kw.trim()
                    if (trimmed.isEmpty()) {
                        // 关键词清空回 Idle 态（恢复引导文案），不残留旧结果
                        _results.value = emptyList()
                        _state.value = RequestState.Idle
                        return@collectLatest
                    }
                    _state.value = RequestState.Loading
                    try {
                        val result = repository.searchRecords(trimmed)
                        if (result.code == 200) {
                            _results.value = result.data?.records.orEmpty()
                            _state.value = RequestState.Success
                        } else {
                            _results.value = emptyList()
                            _state.value = RequestState.Error(
                                messageResId = R.string.div_error_network,
                                message = result.message,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _results.value = emptyList()
                        _state.value = RequestState.Error(
                            messageResId = R.string.div_error_network,
                            message = e.message,
                        )
                    }
                }
        }
    }

    fun onKeywordChange(value: String) {
        _keyword.value = value
    }
}
