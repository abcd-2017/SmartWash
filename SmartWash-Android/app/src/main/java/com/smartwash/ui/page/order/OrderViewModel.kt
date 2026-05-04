package com.smartwash.ui.page.order

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.OrderApi
import com.smartwash.utils.AppConstant
import com.smartwash.paging.OrderPagingSource
import com.smartwash.paging.pagingFlow
import com.smartwash.R
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _orderState = MutableStateFlow<String>("")
    val orderState = _orderState.asStateFlow()
    private val _cancelOrderState = MutableStateFlow<RequestState>(RequestState.Idle)
    val cancelOrderState = _cancelOrderState.asStateFlow()

    val pagingFlow = pagingFlow(orderState) { status ->
        OrderPagingSource(orderApi, status)
    }

    fun updateOrderStatus(status: String) {
        _orderState.value = status
    }

    fun resetCancelOrderState() {
        _cancelOrderState.value = RequestState.Idle
    }

    fun cancelOrder(orderId: Long) {
        _cancelOrderState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val responseData = orderApi.cancelOrder(orderId)
                if (responseData.data == true) {
                    _cancelOrderState.value = RequestState.Success
                }
            } catch (e: Exception) {
                Log.e(AppConstant.APP_NAME, "OrderViewModel.cancelOrder: ${e.message}", e)
                _cancelOrderState.value = RequestState.Error(R.string.error_cancel_order_failed)
            }
        }
    }
}