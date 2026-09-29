package com.smartwash.feature.payment.impl.ui.payment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.payment.impl.PaymentImplConstant
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.common.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 支付成功页的订单信息加载（T6.2 随页迁入）。
 * 原页面复用 app 壳的 OrderDetailViewModel（订单详情页同款薄包装）；迁移后
 * payment-impl 不得依赖 app 壳，故在本域内建同形 ViewModel——逻辑与错误语义
 * 与 OrderDetailViewModel 完全一致（getOrderInfo + NetworkException → Error 态）。
 */
@HiltViewModel
class PaySuccessViewModel @Inject constructor(
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
                Log.e(PaymentImplConstant.APP_NAME, "PaySuccessViewModel.getOrderDetail: ${e.message}", e)
                _getOrderInfoDetail.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetState() {
        _getOrderInfoDetail.value = RequestState.Idle
    }
}
