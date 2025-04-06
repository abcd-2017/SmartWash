package com.smartwash.ui.page.coupon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.CouponApi
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponApi: CouponApi,
) : ViewModel() {
    private val _getCouponListState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getCouponListState = _getCouponListState.asStateFlow()
    private val _couponList = MutableStateFlow<List<CouponVo>>(emptyList())
    val couponList = _couponList.asStateFlow()

    fun getCouponList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = couponApi.getAllCoupon()
                withContext(Dispatchers.Main) {
                    _couponList.value = responseData.data ?: emptyList()
                }
            } catch (e: Exception) {
                _getCouponListState.value = RequestState.Error("${e.message}")
            }
        }
    }

    fun receiveCoupon() {

    }

    fun resetState() {
        _getCouponListState.value = RequestState.Idle
    }
}