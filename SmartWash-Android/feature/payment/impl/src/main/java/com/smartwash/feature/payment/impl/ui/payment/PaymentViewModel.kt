package com.smartwash.feature.payment.impl.ui.payment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.payment.impl.PaymentImplConstant
import com.smartwash.feature.coupon.api.CouponApi
import com.smartwash.feature.coupon.api.model.UsableCoupon
import com.smartwash.feature.payment.impl.network.entity.OrderPayment
import com.smartwash.feature.payment.impl.repository.PaymentRepository
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.common.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val orderApi: OrderApi,
    private val paymentRepository: PaymentRepository,
    private val couponApi: CouponApi,
) : ViewModel() {
    private val _initState = MutableStateFlow<RequestState>(RequestState.Idle)
    val initState = _initState.asStateFlow()
    private val _orderInfo = MutableStateFlow<OrderInfo?>(null)
    val orderInfo = _orderInfo.asStateFlow()
    private val _paymentState = MutableStateFlow<RequestState>(RequestState.Idle)
    val paymentState = _paymentState.asStateFlow()
    private val _getUserCouponState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getUserCouponState = _getUserCouponState.asStateFlow()
    private val _userCouponList = MutableStateFlow<List<UsableCoupon>>(emptyList())
    val userCouponList = _userCouponList.asStateFlow()
    private val _calculationOrderState = MutableStateFlow<RequestState>(RequestState.Idle)
    val calculationOrderState = _calculationOrderState.asStateFlow()

    fun initData(orderId: Long) {
        _initState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                _orderInfo.value = orderApi.getOrderInfo(orderId)
                _initState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(PaymentImplConstant.APP_NAME, "PaymentViewModel.initData: ${e.message}", e)
                _initState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun getaUserCoupon(orderId: Long) {
        _getUserCouponState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val canUseCoupon = couponApi.getCanUseCoupon(orderId)
                _userCouponList.value = canUseCoupon
                _getUserCouponState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(PaymentImplConstant.APP_NAME, "PaymentViewModel.getaUserCoupon: ${e.message}", e)
                _getUserCouponState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun calculationOrder(orderId: Long, userCouponId: Long) {
        _calculationOrderState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                _orderInfo.value = orderApi.calculationOrder(orderId, userCouponId)
                _calculationOrderState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(PaymentImplConstant.APP_NAME, "PaymentViewModel.calculationOrder: ${e.message}", e)
                _calculationOrderState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun paymentOrder(orderId: Long, paymentState: String, userCouponId: Long?) {
        _paymentState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                paymentRepository.payment(OrderPayment(orderId, paymentState, userCouponId))
                _paymentState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(PaymentImplConstant.APP_NAME, "PaymentViewModel.paymentOrder: ${e.message}", e)
                _paymentState.value = RequestState.Error(e.resId, e.message)
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
