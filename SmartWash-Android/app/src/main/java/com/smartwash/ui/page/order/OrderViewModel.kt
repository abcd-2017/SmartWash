package com.smartwash.ui.page.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.smartwash.network.api.OrderApi
import com.smartwash.paging.OrderPagingSource
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _orderState = MutableStateFlow<String>("")
    val orderState = _orderState.asStateFlow()
    private val _cancelOrderState = MutableStateFlow<RequestState>(RequestState.Idle)
    val cancelOrderState = _cancelOrderState.asStateFlow()

    fun updateOrderStatus(status: String) {
        _orderState.value = status
    }

    fun resetCancelOrderState() {
        _cancelOrderState.value = RequestState.Idle
    }

    fun cancelOrder(orderId: Long) {
        _cancelOrderState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = orderApi.cancelOrder(orderId)
                withContext(Dispatchers.Main) {
                    if (responseData.data == true) {
                        _cancelOrderState.value = RequestState.Success
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _cancelOrderState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingFlow = orderState
        .debounce(300)//防抖
        .distinctUntilChanged()
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 10)) {
                OrderPagingSource(orderApi, it)
            }.flow.cachedIn(viewModelScope)
        }
}