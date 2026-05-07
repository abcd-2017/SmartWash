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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _ordersMap = MutableStateFlow<Map<String, List<OrderInfo>>>(emptyMap())
    val ordersMap = _ordersMap.asStateFlow()

    private val _hasMoreMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val _loadingMoreMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingMoreMap = _loadingMoreMap.asStateFlow()

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

                _ordersMap.value = orders
                _hasMoreMap.value = hasMore
                _pageMap.clear()
                _pageMap.putAll(pages)
                _loadState.value = RequestState.Success
            } catch (e: Exception) {
                Log.e(AppConstant.APP_NAME, "OrderViewModel.loadAllOrders: ${e.message}", e)
                _loadState.value = RequestState.Error(R.string.error_network_fail)
            }
        }
    }

    fun loadMore(status: String) {
        val currentHasMore = _hasMoreMap.value[status] ?: false
        val currentLoading = _loadingMoreMap.value[status] ?: false
        if (!currentHasMore || currentLoading) return

        _loadingMoreMap.value = _loadingMoreMap.value.toMutableMap().apply { put(status, true) }

        viewModelScope.launch {
            try {
                val nextPage = (_pageMap[status] ?: 1) + 1
                val newItems = orderRepository.getOrderList(status, nextPage)

                val currentList = _ordersMap.value[status] ?: emptyList()
                _ordersMap.value = _ordersMap.value.toMutableMap().apply {
                    put(status, currentList + newItems)
                }
                _hasMoreMap.value = _hasMoreMap.value.toMutableMap().apply {
                    put(status, newItems.size >= 10)
                }
                _pageMap[status] = nextPage
            } catch (e: Exception) {
                Log.e(AppConstant.APP_NAME, "OrderViewModel.loadMore: ${e.message}", e)
            } finally {
                _loadingMoreMap.value = _loadingMoreMap.value.toMutableMap().apply { put(status, false) }
            }
        }
    }

    fun hasMoreForStatus(status: String): Boolean {
        return _hasMoreMap.value[status] ?: false
    }

    fun cancelOrder(orderId: Long) {
        _cancelOrderState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                orderRepository.cancelOrder(orderId)
                _cancelOrderState.value = RequestState.Success
                loadAllOrders()
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
