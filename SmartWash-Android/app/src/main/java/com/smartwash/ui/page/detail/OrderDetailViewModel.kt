package com.smartwash.ui.page.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.R
import com.smartwash.common.utils.model.RequestState
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
                // 订单详情为低频加载入口（进页面一次），成功留 info 便于对账
                AppLogger.info(TAG, "订单详情加载成功：orderId=$orderId")
                _getOrderInfoDetail.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "OrderDetailViewModel.getOrderDetail 失败：orderId=$orderId", e)
                _getOrderInfoDetail.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetState() {
        _getOrderInfoDetail.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "OrderDetailViewModel"
    }
}
