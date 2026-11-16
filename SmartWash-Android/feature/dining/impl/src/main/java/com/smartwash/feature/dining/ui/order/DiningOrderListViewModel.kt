package com.smartwash.feature.dining.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.logic.DiningOrderListItem
import com.smartwash.feature.dining.logic.toOrderListItem
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 订单列表分页大小（后端 BaseSearchFrom 默认 10、上限 100；订单量级小取 20 平衡首屏与请求数） */
private const val PAGE_SIZE = 20

/**
 * 订单列表页 ViewModel（域首页）：我的订单（我发起的 + 我参与的）手动分页，
 * 页面按 isCreator 拆分区渲染（后端单接口倒序返回，逐页追加）。
 *
 * 状态划分（请求状态全程 [RequestState]）：
 * - [listState]：列表主流程（骨架/错误/内容），下拉刷新与重试复用；
 * - [loadMoreState]：加载更多独立态，失败不封底、footer 内联重试；
 * - [hasMore]：是否还有下一页（累计已加载条数与 total 比较）；
 * - [orders]：映射后列表项，分区派生放页面 remember（组合期纯计算，非副作用）。
 *
 * 首载/刷新与加载更多互斥：[loadMore] 在首载或刷新进行中直接忽略，避免响应交错
 * 把旧页数据追加进已重置的列表。
 */
@HiltViewModel
class DiningOrderListViewModel @Inject constructor(
    private val diningRepository: DiningRepository,
) : ViewModel() {

    private val _listState = MutableStateFlow<RequestState>(RequestState.Idle)
    val listState = _listState.asStateFlow()

    private val _orders = MutableStateFlow<List<DiningOrderListItem>>(emptyList())
    val orders = _orders.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private val _loadMoreState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadMoreState = _loadMoreState.asStateFlow()

    private var currentPage = 1
    private var total = 0L

    /** 首载/重试/下拉刷新（防重：加载中直接忽略） */
    fun load() {
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                val page = diningRepository.getMyOrders(1, PAGE_SIZE)
                currentPage = 1
                total = page.total
                _orders.value = page.records.map { it.toOrderListItem() }
                _hasMore.value = _orders.value.size < total
                _loadMoreState.value = RequestState.Idle
                _listState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderListViewModel.load: ${e.message}", e)
                _listState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 加载更多（防重：加载中/无更多/首载刷新中直接忽略） */
    fun loadMore() {
        if (_loadMoreState.value is RequestState.Loading || !_hasMore.value) return
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _loadMoreState.value = RequestState.Loading
            try {
                val nextPage = currentPage + 1
                val page = diningRepository.getMyOrders(nextPage, PAGE_SIZE)
                currentPage = nextPage
                total = page.total
                _orders.value = _orders.value + page.records.map { it.toOrderListItem() }
                _hasMore.value = _orders.value.size < total
                _loadMoreState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderListViewModel.loadMore: ${e.message}", e)
                _loadMoreState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    private companion object {
        const val TAG = "DiningOrderListViewModel"
    }
}
