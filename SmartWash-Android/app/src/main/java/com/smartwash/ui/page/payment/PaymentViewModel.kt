package com.smartwash.ui.page.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.PaymentApi
import com.smartwash.network.entity.OrderPayment
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
class PaymentViewModel @Inject constructor(
    private val orderApi: OrderApi,
    private val paymentApi: PaymentApi
) : ViewModel() {
    private val _initState = MutableStateFlow<RequestState>(RequestState.Idle)
    val initState = _initState.asStateFlow()
    private val _orderInfo = MutableStateFlow<OrderInfo?>(null)
    val orderInfo = _orderInfo.asStateFlow()
    private val _paymentState = MutableStateFlow<RequestState>(RequestState.Idle)
    val paymentState = _paymentState.asStateFlow()

    fun initData(orderId: Long) {
        _initState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val orderInfoRes = orderApi.getOrderInfo(orderId)

                withContext(Dispatchers.Main) {
                    _orderInfo.value = orderInfoRes.data
                    _initState.value = RequestState.Success
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _initState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    fun paymentOrder(orderId: Long, paymentState: String, amount: Float) {
        _paymentState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = paymentApi.payment(OrderPayment(orderId, amount, paymentState))
                withContext(Dispatchers.Main) {
                    _paymentState.value = RequestState.Success
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _paymentState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    fun resetPaymentState() {
        _paymentState.value = RequestState.Idle
    }

    fun resetInitState() {
        _initState.value = RequestState.Idle
    }
}