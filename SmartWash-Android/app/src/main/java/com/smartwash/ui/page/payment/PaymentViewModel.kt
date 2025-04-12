package com.smartwash.ui.page.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.CouponApi
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.PaymentApi
import com.smartwash.network.entity.OrderPayment
import com.smartwash.network.vo.coupon.UserCouponVo
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
    private val paymentApi: PaymentApi,
    private val couponApi: CouponApi,
) : ViewModel() {
    private val _initState = MutableStateFlow<RequestState>(RequestState.Idle)
    val initState = _initState.asStateFlow()
    private val _orderInfo = MutableStateFlow<OrderInfo?>(null)
    val orderInfo = _orderInfo.asStateFlow()
    private val _paymentState = MutableStateFlow<RequestState>(RequestState.Idle)
    val paymentState = _paymentState.asStateFlow()

    private val _couponState = MutableStateFlow<String>("0")
    private val couponState = _couponState.asStateFlow()

    private val _getUserCouponState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getUserCouponState = _getUserCouponState.asStateFlow()

    private val _userCouponList = MutableStateFlow<List<UserCouponVo>>(emptyList())
    val userCouponList = _userCouponList.asStateFlow()

    private val _calculationOrderState = MutableStateFlow<RequestState>(RequestState.Idle)
    val calculationOrderState = _calculationOrderState.asStateFlow()

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

    //获取已经领取的优惠券列表
    fun getaUserCoupon(orderId: Long) {
        _getUserCouponState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val canUseCoupon = couponApi.getCanUseCoupon(orderId)
                _userCouponList.value = canUseCoupon.data ?: emptyList()
                _getUserCouponState.value = RequestState.Success
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _getUserCouponState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    //计算使用优惠券后订单价格
    fun calculationOrder(orderId: Long, userCouponId: Long) {
        _calculationOrderState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = orderApi.calculationOrder(orderId, userCouponId)
                withContext(Dispatchers.Main) {
                    _orderInfo.value = responseData.data
                    _calculationOrderState.value = RequestState.Success
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _calculationOrderState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    fun paymentOrder(orderId: Long, paymentState: String, userCouponId: Long?) {
        _paymentState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData =
                    paymentApi.payment(OrderPayment(orderId, paymentState, userCouponId))
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