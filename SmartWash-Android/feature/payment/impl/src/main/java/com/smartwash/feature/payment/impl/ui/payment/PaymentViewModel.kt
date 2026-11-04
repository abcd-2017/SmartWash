package com.smartwash.feature.payment.impl.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.coupon.api.CouponApi
import com.smartwash.feature.coupon.api.model.UsableCoupon
import com.smartwash.feature.payment.impl.network.entity.OrderPayment
import com.smartwash.feature.payment.impl.repository.PaymentRepository
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.common.utils.model.RequestState
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
                AppLogger.error(TAG, "PaymentViewModel.initData: ${e.message}", e)
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
                AppLogger.error(TAG, "PaymentViewModel.getaUserCoupon: ${e.message}", e)
                _getUserCouponState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun calculationOrder(orderId: Long, userCouponId: Long) {
        _calculationOrderState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val recalculated = orderApi.calculationOrder(orderId, userCouponId)
                _orderInfo.value = recalculated
                // 低频关键动作成功：选券试算完成（结果直接决定应付金额，留痕业务键）
                AppLogger.info(
                    TAG,
                    "calculationOrder: 支付试算完成 orderId=$orderId userCouponId=$userCouponId payPrice=${recalculated.payPrice}"
                )
                _calculationOrderState.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PaymentViewModel.calculationOrder: ${e.message}", e)
                _calculationOrderState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun paymentOrder(orderId: Long, paymentState: String, userCouponId: Long?) {
        _paymentState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                // 发起支付以 debug 留业务键：后端业务拒绝（余额不足/优惠券异常/已支付等）经
                // ResponseInterceptor 统一 warn 留痕但不带订单号，凭本行在 debug 构建对齐订单
                AppLogger.debug(
                    TAG,
                    "paymentOrder: 发起支付 orderId=$orderId paymentType=$paymentState userCouponId=$userCouponId"
                )
                paymentRepository.payment(OrderPayment(orderId, paymentState, userCouponId))
                // 低频关键动作成功：订单支付完成（资金链路核心留痕，payPrice 为支付前试算应付金额）
                AppLogger.info(
                    TAG,
                    "paymentOrder: 订单支付成功 orderId=$orderId paymentType=$paymentState " +
                        "userCouponId=$userCouponId payPrice=${_orderInfo.value?.payPrice}"
                )
                _paymentState.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "PaymentViewModel.paymentOrder: ${e.message}", e)
                _paymentState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetPaymentState() {
        _paymentState.value = RequestState.Idle
    }

    fun resetCalculationOrderState() {
        _calculationOrderState.value = RequestState.Idle
    }

    fun resetInitState() {
        _initState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "PaymentViewModel"
    }
}
