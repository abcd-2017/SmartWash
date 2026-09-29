package com.smartwash.ui.page.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.R
import com.smartwash.common.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _getOrderInfoDetail = MutableStateFlow<RequestState>(RequestState.Idle)
    val getOrderInfoDetail = _getOrderInfoDetail.asStateFlow()
    private val _orderInfo = MutableStateFlow<OrderInfo?>(null)
    val orderInfo = _orderInfo.asStateFlow()

    fun getOrderDetail(orderId: Long) {
        _getOrderInfoDetail.value = RequestState.Loading
        viewModelScope.launch {
            try {
                _orderInfo.value = orderApi.getOrderInfo(orderId)
                _getOrderInfoDetail.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "OrderDetailViewModel.getOrderDetail: ${e.message}", e)
                _getOrderInfoDetail.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetState() {
        _getOrderInfoDetail.value = RequestState.Idle
    }
}
