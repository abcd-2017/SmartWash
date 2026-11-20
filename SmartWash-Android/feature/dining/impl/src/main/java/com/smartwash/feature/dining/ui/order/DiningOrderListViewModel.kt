package com.smartwash.feature.dining.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.api.model.DiningOrderActiveVo
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

    /**
     * 我发起的进行中 DRAFT（唯一性收敛）：null=无。列表 FAB / lite 主 CTA 据此决定
     * 「进该单编辑态」还是「空白创建页」；每次首载/刷新/回前台随列表一并更新。
     */
    private val _activeOrder = MutableStateFlow<DiningOrderActiveVo?>(null)
    val activeOrder = _activeOrder.asStateFlow()

    /** 放弃草稿动作态（Loading 防重；成功一次性 Toast 后消费） */
    private val _cancelState = MutableStateFlow<RequestState>(RequestState.Idle)
    val cancelState = _cancelState.asStateFlow()

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
                // 唯一性收敛：拉取本人进行中的 DRAFT，供 FAB/CTA 决定进编辑态还是空白创建页；
                // active 查询失败静默置空（最坏退回新建，后端仍会以 40901 兜底提示）
                _activeOrder.value = runCatching { diningRepository.getActiveOrder() }.getOrNull()
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

    /**
     * 放弃草稿（仅发起者的 DRAFT；二次确认由页面承担）：成功后刷新列表与 active。
     * 后端拒绝（非发起者/已结束/已取消）转译为 NetworkException 上抛，由页面 Toast。
     */
    fun cancelOrder(orderId: Long) {
        if (orderId <= 0L) return
        if (_cancelState.value is RequestState.Loading) return
        viewModelScope.launch {
            _cancelState.value = RequestState.Loading
            try {
                diningRepository.cancelOrder(orderId)
                _cancelState.value = RequestState.Success
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderListViewModel.cancelOrder: ${e.message}", e)
                _cancelState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 消费放弃动作态（页面 Toast 后调用，避免重放） */
    fun consumeCancelState() {
        _cancelState.value = RequestState.Idle
    }

    private companion object {
        const val TAG = "DiningOrderListViewModel"
    }
}
