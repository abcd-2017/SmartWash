package com.smartwash.ui.page.order

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.R
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.repository.OrderRepository
import com.smartwash.utils.AppConstant
import com.smartwash.utils.RequestState
import com.smartwash.utils.ShowOrderStatus
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
                Log.e(AppConstant.APP_NAME, "OrderViewModel.loadAllOrders: ${e.message}", e)
                _loadState.value = RequestState.Error(R.string.error_network_fail)
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
                    hasMore = state.hasMore + (status to (newItems.size >= AppConstant.PAGE_SIZE)),
                )
                _pageMap[status] = nextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AppConstant.APP_NAME, "OrderViewModel.loadMore: ${e.message}", e)
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
                Log.e(AppConstant.APP_NAME, "OrderViewModel.cancelOrder: ${e.message}", e)
                _cancelOrderState.value = RequestState.Error(R.string.error_cancel_order_failed)
            }
        }
    }

    fun resetCancelOrderState() {
        _cancelOrderState.value = RequestState.Idle
    }
}
