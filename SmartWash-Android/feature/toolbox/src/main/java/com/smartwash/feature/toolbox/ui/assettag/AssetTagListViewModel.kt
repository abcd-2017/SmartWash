package com.smartwash.feature.toolbox.ui.assettag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.toolbox.network.AssetTagVo
import com.smartwash.feature.toolbox.repository.AssetTagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 物品标签列表 ViewModel：手动分页（镜像鸿蒙 PagingController 语义，每页 10）；
 * 创建/更新/删除成功回列表经 savedStateHandle 变更信号触发 refresh（对齐短码列表）。
 */
@HiltViewModel
class AssetTagListViewModel @Inject constructor(
    private val repository: AssetTagRepository,
) : ViewModel() {

    /** 状态数据类：列表页四态（首刷骨架/首刷错误/空态/列表+尾部） */
    data class ListState(
        val items: List<AssetTagVo> = emptyList(),
        val loadingFirst: Boolean = true,
        val firstError: Boolean = false,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val appendError: Boolean = false,
        val endReached: Boolean = false,
        val page: Int = 0,
    )

    private val _state = MutableStateFlow(ListState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true, appendError = false)
        loadPage(1)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.endReached || s.loadingFirst || s.firstError) {
            return
        }
        loadPage(s.page + 1)
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = page > 1)
            try {
                val data = repository.pageAssetTags(page, PAGE_SIZE)
                val merged = if (page == 1) data.records else _state.value.items + data.records
                _state.value = _state.value.copy(
                    items = merged,
                    loadingFirst = false,
                    firstError = false,
                    refreshing = false,
                    loadingMore = false,
                    appendError = false,
                    endReached = merged.size >= data.total,
                    page = page,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "pageAssetTags: ${e.message}", e)
                onFail(page)
            } catch (e: Exception) {
                AppLogger.error(TAG, "pageAssetTags: ${e.message}", e)
                onFail(page)
            }
        }
    }

    private fun onFail(page: Int) {
        _state.value = if (page == 1) {
            _state.value.copy(loadingFirst = false, firstError = true, refreshing = false, loadingMore = false)
        } else {
            _state.value.copy(loadingMore = false, appendError = true)
        }
    }

    private companion object {
        const val TAG = "AssetTagListViewModel"
        const val PAGE_SIZE = 10
    }
}
