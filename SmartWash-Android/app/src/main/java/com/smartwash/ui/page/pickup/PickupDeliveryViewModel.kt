package com.smartwash.ui.page.pickup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppConstant
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.PickupDeliveryType
import com.smartwash.R
import com.smartwash.common.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PickupDeliveryViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _getOrderInfoDetail = MutableStateFlow<RequestState>(RequestState.Idle)
    val getOrderInfoDetail = _getOrderInfoDetail.asStateFlow()
    private val _orderInfo = MutableStateFlow<OrderInfo?>(null)
    val orderInfo = _orderInfo.asStateFlow()
    private val _setOrderNextState = MutableStateFlow<RequestState>(RequestState.Idle)
    val setOrderNextState = _setOrderNextState.asStateFlow()

    fun getOrderDetail(orderId: Long) {
        _getOrderInfoDetail.value = RequestState.Loading
        viewModelScope.launch {
            try {
                _orderInfo.value = orderApi.getOrderInfo(orderId)
                _getOrderInfoDetail.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "PickupDeliveryViewModel.getOrderDetail: ${e.message}", e)
                _getOrderInfoDetail.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun setOrderNextState(type: Int, orderId: Long, pickupCode: String) {
        _setOrderNextState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val success = if (type == PickupDeliveryType.DELIVERY.type) {
                    orderApi.shippingOrder(orderId, pickupCode)
                } else {
                    orderApi.pickupOrder(orderId, pickupCode)
                }
                if (success) {
                    _setOrderNextState.value = RequestState.Success
                } else {
                    _setOrderNextState.value = RequestState.Error(R.string.error_operation_failed)
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "PickupDeliveryViewModel.setOrderNextState: ${e.message}", e)
                _setOrderNextState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetNextStatusState() {
        _setOrderNextState.value = RequestState.Idle
    }

    fun resetState() {
        _getOrderInfoDetail.value = RequestState.Idle
    }
}
