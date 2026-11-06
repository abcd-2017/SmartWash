package com.smartwash.feature.order.impl.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.ShowOrderStatus
import com.smartwash.feature.order.impl.OrderImplConstant
import com.smartwash.feature.order.impl.R
import com.smartwash.feature.order.impl.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 订单页单一 UiState：列表 / hasMore / 加载更多合并为一次发射，避免多次发射间读到陈旧 hasMore */
data class OrderUiState(
    val orders: Map<String, List<OrderInfo>> = emptyMap(),
    val hasMore: Map<String, Boolean> = emptyMap(),
    val loadingMore: Map<String, Boolean> = emptyMap(),
)

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState = _uiState.asStateFlow()

    private val _pageMap = mutableMapOf<String, Int>()

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _cancelOrderState = MutableStateFlow<RequestState>(RequestState.Idle)
    val cancelOrderState = _cancelOrderState.asStateFlow()

    init {
        loadAllOrders()
    }

    fun loadAllOrders() {
        _loadState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val groupMap = orderRepository.getOrderGroup()
                val orders = mutableMapOf<String, List<OrderInfo>>()
                val hasMore = mutableMapOf<String, Boolean>()
                val pages = mutableMapOf<String, Int>()

                for ((status, group) in groupMap) {
                    orders[status] = group.items
                    hasMore[status] = group.hasMore
                    pages[status] = 1
                }

                for (entry in ShowOrderStatus.entries) {
                    if (entry.status !in orders) {
                        orders[entry.status] = emptyList()
                        hasMore[entry.status] = false
                        pages[entry.status] = 1
                    }
                }

                // 单一 UiState 一次性发射，orders 与 hasMore 不会出现发射间错位
                _uiState.value = OrderUiState(orders = orders, hasMore = hasMore)
                _pageMap.clear()
                _pageMap.putAll(pages)
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "OrderViewModel.loadAllOrders: ${e.message}", e)
                // 具体文案透出（ResponseInterceptor 已转译 data 语义），不再是裸「请求失败」
                _loadState.value = RequestState.Error(
                    (e as? NetworkException)?.resId ?: R.string.error_network_fail,
                    e.message,
                )
            }
        }
    }

    /**
     * 按已加载页数刷新（回归 2026-11-06 N-05-02）：ON_RESUME（订单详情/支付/寄件页返回）
     * 与下拉刷新共用。原实现直接 [loadAllOrders]——summary 接口只回每 status 第一页
     * （size=10），静默丢弃用户已翻到的第 2+ 页，返回后列表被钳制替换、滚动跳动。
     *
     * 策略：以各 status 已加载页数（[_pageMap]）重新拉取并整体替换，已翻页的 status
     * 逐页补拉——分页深度与滚动位置都保持；任一页失败则整体放弃（不发射，旧数据原样保留）。
     * 首刷失败/未加载（无任何数据）时转 [loadAllOrders]（带骨架与错误态流转）。
     */
    fun refreshLoadedOrders() {
        if (_uiState.value.orders.isEmpty() || _loadState.value is RequestState.Error) {
            loadAllOrders()
            return
        }
        // 防重入：ON_RESUME 与下拉短窗口可能并发触发
        if (_loadState.value is RequestState.Loading) return
        _loadState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val loadedPages = _pageMap.toMap()
                // 第一页统一走 summary（一次请求覆盖全部 status）
                val groupMap = orderRepository.getOrderGroup()
                val orders = mutableMapOf<String, List<OrderInfo>>()
                val hasMore = mutableMapOf<String, Boolean>()
                for ((status, group) in groupMap) {
                    orders[status] = group.items
                    hasMore[status] = group.hasMore
                }
                // 已翻过页（_pageMap > 1）的 status 逐页补拉第 2..N 页；某页不满页即无后续，短路停止
                for ((status, page) in loadedPages) {
                    for (p in 2..page) {
                        if (hasMore[status] != true) break
                        val items = orderRepository.getOrderList(status, p)
                        orders[status] = orders[status].orEmpty() + items
                        hasMore[status] = items.size >= OrderImplConstant.PAGE_SIZE
                    }
                }
                for (entry in ShowOrderStatus.entries) {
                    if (entry.status !in orders) {
                        orders[entry.status] = emptyList()
                        hasMore[entry.status] = false
                    }
                }
                _uiState.value = OrderUiState(orders = orders, hasMore = hasMore)
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 刷新失败但旧数据完好：静默保留（不发射新状态），仅留痕
                AppLogger.warn(TAG, "refreshLoadedOrders 失败，保留现有列表", e)
                _loadState.value = RequestState.Success
            }
        }
    }

    fun loadMore(status: String) {
        val current = _uiState.value
        if (current.hasMore[status] != true || current.loadingMore[status] == true) return

        _uiState.value = current.copy(loadingMore = current.loadingMore + (status to true))

        viewModelScope.launch {
            try {
                val nextPage = (_pageMap[status] ?: 1) + 1
                val newItems = orderRepository.getOrderList(status, nextPage)

                val state = _uiState.value
                _uiState.value = state.copy(
                    orders = state.orders + (status to (state.orders[status].orEmpty() + newItems)),
                    hasMore = state.hasMore + (status to (newItems.size >= OrderImplConstant.PAGE_SIZE)),
                )
                _pageMap[status] = nextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "OrderViewModel.loadMore: ${e.message}", e)
            } finally {
                val state = _uiState.value
                _uiState.value = state.copy(loadingMore = state.loadingMore + (status to false))
            }
        }
    }

    fun cancelOrder(orderId: Long) {
        _cancelOrderState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                orderRepository.cancelOrder(orderId)
                _cancelOrderState.value = RequestState.Success
                loadAllOrders()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "OrderViewModel.cancelOrder: ${e.message}", e)
                _cancelOrderState.value = RequestState.Error(R.string.error_cancel_order_failed)
            }
        }
    }

    fun resetCancelOrderState() {
        _cancelOrderState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "OrderViewModel"
    }
}
