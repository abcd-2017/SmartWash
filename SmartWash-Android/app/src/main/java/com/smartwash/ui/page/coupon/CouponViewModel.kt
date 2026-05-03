package com.smartwash.ui.page.coupon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.CouponApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.paging.UserCouponPagingSource
import com.smartwash.paging.pagingFlow
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponApi: CouponApi,
) : ViewModel() {
    private val _getCouponListState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getCouponListState = _getCouponListState.asStateFlow()
    private val _couponList = MutableStateFlow<List<CouponVo>>(emptyList())
    val couponList = _couponList.asStateFlow()
    private val _receiveCouponState = MutableStateFlow<RequestState>(RequestState.Idle)
    val receiveCouponState = _receiveCouponState.asStateFlow()
    private val _couponState = MutableStateFlow<String>("0")
    val couponState = _couponState.asStateFlow()

    fun updateCouponState(status: String) {
        _couponState.value = status
    }

    val userCouponPagingFlow = pagingFlow(couponState) { status ->
        UserCouponPagingSource(couponApi, status)
    }

    fun getCouponList() {
        viewModelScope.launch {
            try {
                val responseData = couponApi.getAllCoupon()
                _couponList.value = responseData.data ?: emptyList()
            } catch (e: NetworkException) {
                _getCouponListState.value = RequestState.Error(e.message ?: "获取优惠券列表失败")
            }
        }
    }

    fun receiveCoupon(couponId: Long) {
        viewModelScope.launch {
            try {
                val responseData = couponApi.receiveCoupon(couponId)
                if (responseData.data == true) {
                    _receiveCouponState.value = RequestState.Success
                }
            } catch (e: NetworkException) {
                _receiveCouponState.value = RequestState.Error(e.message ?: "领取优惠券失败")
            }
        }
    }

    fun resetReceiveState() {
        _receiveCouponState.value = RequestState.Idle
    }

    fun resetState() {
        _getCouponListState.value = RequestState.Idle
    }
}