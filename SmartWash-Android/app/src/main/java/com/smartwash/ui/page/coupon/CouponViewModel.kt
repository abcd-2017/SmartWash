package com.smartwash.ui.page.coupon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.smartwash.network.api.CouponApi
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.paging.UserCouponPagingSource
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    private val _receiveCouponState = MutableStateFlow<RequestState>(RequestState.Idle)
    val receiveCouponState = _receiveCouponState.asStateFlow()
    private val _couponState = MutableStateFlow<String>("0")
    val couponState = _couponState.asStateFlow()

    fun updateCouponState(status: String) {
        _couponState.value = status
    }

    //获取已经领取的优惠券列表
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val userCouponPagingFlow = couponState
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 10)) {
                UserCouponPagingSource(couponApi, it)
            }.flow.cachedIn(viewModelScope)
        }

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

    fun receiveCoupon(couponId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = couponApi.receiveCoupon(couponId)
                if (responseData.data == true) {
                    withContext(Dispatchers.Main) {
                        _receiveCouponState.value = RequestState.Success
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _receiveCouponState.value = RequestState.Error("${e.message}")
                }
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