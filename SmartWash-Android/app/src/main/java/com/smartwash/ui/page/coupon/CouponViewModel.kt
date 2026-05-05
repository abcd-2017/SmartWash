package com.smartwash.ui.page.coupon

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.CouponApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.utils.AppConstant
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

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _availableCoupons = MutableStateFlow<List<CouponVo>>(emptyList())
    val availableCoupons = _availableCoupons.asStateFlow()

    private val _claimedCoupons = MutableStateFlow<List<UserCouponVo>>(emptyList())
    val claimedCoupons = _claimedCoupons.asStateFlow()

    private val _historicalCoupons = MutableStateFlow<List<UserCouponVo>>(emptyList())
    val historicalCoupons = _historicalCoupons.asStateFlow()

    private val _receiveCouponState = MutableStateFlow<RequestState>(RequestState.Idle)
    val receiveCouponState = _receiveCouponState.asStateFlow()

    fun loadAllCoupons() {
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                val data = couponApi.getAllCoupons().data
                _availableCoupons.value = data?.available ?: emptyList()
                _claimedCoupons.value = data?.claimed ?: emptyList()
                _historicalCoupons.value = data?.historical ?: emptyList()
                _loadState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "CouponViewModel.loadAllCoupons: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun receiveCoupon(couponId: Long) {
        viewModelScope.launch {
            try {
                couponApi.receiveCoupon(couponId)
                _receiveCouponState.value = RequestState.Success
                loadAllCoupons()
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "CouponViewModel.receiveCoupon: ${e.message}", e)
                _receiveCouponState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetReceiveState() {
        _receiveCouponState.value = RequestState.Idle
    }

    fun resetState() {
        _loadState.value = RequestState.Idle
    }
}
