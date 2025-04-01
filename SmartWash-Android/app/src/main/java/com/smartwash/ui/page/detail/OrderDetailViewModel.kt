package com.smartwash.ui.page.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.OrderApi
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val orderApi: OrderApi
) : ViewModel() {
    private val _getOrderInfoDetail = MutableStateFlow<RequestState>(RequestState.Idle)
    val getOrderInfoDetail = _getOrderInfoDetail.asStateFlow()
    private val _orderInfo = MutableStateFlow<OrderInfo?>(null)
    val orderInfo = _orderInfo.asStateFlow()

    fun getOrderDetail(orderId: Long) {
        _getOrderInfoDetail.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = orderApi.getOrderInfo(orderId)
                withContext(Dispatchers.Main) {
                    _orderInfo.value = responseData.data
                    _getOrderInfoDetail.value = RequestState.Success
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _getOrderInfoDetail.value = RequestState.Error("$e")
                }
            }
        }
    }

    fun resetState() {
        _getOrderInfoDetail.value = RequestState.Idle
    }
}