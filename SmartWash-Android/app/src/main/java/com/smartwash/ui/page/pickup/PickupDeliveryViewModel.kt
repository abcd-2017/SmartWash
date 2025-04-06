package com.smartwash.ui.page.pickup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.OrderApi
import com.smartwash.network.entity.ResponseData
import com.smartwash.network.entity.order.OrderNextStatus
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.utils.PickupDeliveryType
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun setOrderNextState(type: Int, orderId: Long, pickupCode: String) {
        _setOrderNextState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextStatus = OrderNextStatus(orderId, pickupCode)
                val res: ResponseData<Boolean>
                if (type == PickupDeliveryType.DELIVERY.type) {
                    res = orderApi.shippingOrder(nextStatus)
                } else {
                    res = orderApi.pickupOrder(nextStatus)
                }
                withContext(Dispatchers.Main) {
                    if (res.data == true) {
                        _setOrderNextState.value = RequestState.Success
                    } else {
                        _setOrderNextState.value = RequestState.Error("操作失败！")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _setOrderNextState.value = RequestState.Error("${e.message}")
                }
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