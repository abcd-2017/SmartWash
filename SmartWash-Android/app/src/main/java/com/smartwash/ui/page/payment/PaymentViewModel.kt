package com.smartwash.ui.page.payment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.CouponApi
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.PaymentApi
import com.smartwash.network.entity.OrderPayment
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.R
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val orderApi: OrderApi,
    private val paymentApi: PaymentApi,
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
    private val _userCouponList = MutableStateFlow<List<UserCouponVo>>(emptyList())
    val userCouponList = _userCouponList.asStateFlow()
    private val _calculationOrderState = MutableStateFlow<RequestState>(RequestState.Idle)
    val calculationOrderState = _calculationOrderState.asStateFlow()

    fun initData(orderId: Long) {
        _initState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val orderInfoRes = orderApi.getOrderInfo(orderId)
                _orderInfo.value = orderInfoRes.data
                _initState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "PaymentViewModel.initData: ${e.message}", e)
                _initState.value = RequestState.Error(e.resId)
            }
        }
    }

    fun getaUserCoupon(orderId: Long) {
        _getUserCouponState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val canUseCoupon = couponApi.getCanUseCoupon(orderId)
                _userCouponList.value = canUseCoupon.data ?: emptyList()
                _getUserCouponState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "PaymentViewModel.getaUserCoupon: ${e.message}", e)
                _getUserCouponState.value = RequestState.Error(e.resId)
            }
        }
    }

    fun calculationOrder(orderId: Long, userCouponId: Long) {
        _calculationOrderState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val responseData = orderApi.calculationOrder(orderId, userCouponId)
                _orderInfo.value = responseData.data
                _calculationOrderState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "PaymentViewModel.calculationOrder: ${e.message}", e)
                _calculationOrderState.value = RequestState.Error(e.resId)
            }
        }
    }

    fun paymentOrder(orderId: Long, paymentState: String, userCouponId: Long?) {
        _paymentState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                paymentApi.payment(OrderPayment(orderId, paymentState, userCouponId))
                _paymentState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "PaymentViewModel.paymentOrder: ${e.message}", e)
                _paymentState.value = RequestState.Error(e.resId)
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