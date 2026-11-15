package com.smartwash.ui.page.pickup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.PickupDeliveryType
import com.smartwash.R
import com.smartwash.common.utils.model.RequestState
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
                // 寄件/取件详情为低频加载入口（进页面一次），成功留 info 便于对账
                AppLogger.info(TAG, "寄件/取件详情加载成功：orderId=$orderId")
                _getOrderInfoDetail.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PickupDeliveryViewModel.getOrderDetail 失败：orderId=$orderId", e)
                _getOrderInfoDetail.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun setOrderNextState(type: Int, orderId: Long, pickupCode: String) {
        // 敏感红线：pickupCode 全程不入日志（消息只允许出现 orderId 与操作类型）
        val actionName = if (type == PickupDeliveryType.DELIVERY.type) "寄件" else "取件"
        _setOrderNextState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val success = if (type == PickupDeliveryType.DELIVERY.type) {
                    orderApi.shippingOrder(orderId, pickupCode)
                } else {
                    orderApi.pickupOrder(orderId, pickupCode)
                }
                if (success) {
                    // 低频关键动作成功（取件确认/寄件确认），release 亦可对账
                    AppLogger.info(TAG, "${actionName}确认成功：orderId=$orderId")
                    _setOrderNextState.value = RequestState.Success
                } else {
                    // 业务拒绝（如取件码校验失败）：只记失败事实与 orderId，绝不记取件码值
                    AppLogger.warn(TAG, "${actionName}确认被业务拒绝：orderId=$orderId")
                    _setOrderNextState.value = RequestState.Error(R.string.error_operation_failed)
                }
            } catch (e: NetworkException) {
                AppLogger.error(
                    TAG, "PickupDeliveryViewModel.setOrderNextState 失败：action=$actionName orderId=$orderId", e
                )
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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "PickupDeliveryViewModel"
    }
}
