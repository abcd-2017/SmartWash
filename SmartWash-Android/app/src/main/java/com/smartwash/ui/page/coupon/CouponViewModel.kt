package com.smartwash.ui.page.coupon

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.database.dao.CouponVoDao
import com.smartwash.network.api.CouponApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.paging.UserCouponPagingSource
import com.smartwash.paging.pagingFlow
import com.smartwash.R
import com.smartwash.repository.CouponRepository
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponRepository: CouponRepository,
    private val couponVoDao: CouponVoDao,
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
            // 有缓存时先显示缓存，无缓存时显示 Loading
            val cached = couponVoDao.getAll()
            if (cached.isNotEmpty()) {
                _couponList.value = cached.map { it.toVo() }
            } else {
                _getCouponListState.value = RequestState.Loading
            }

            try {
                _couponList.value = couponRepository.getAllCoupon()
                _getCouponListState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "CouponViewModel.getCouponList: ${e.message}", e)
                if (cached.isEmpty()) {
                    _getCouponListState.value = RequestState.Error(e.resId, e.message)
                }
            }
        }
    }

    fun receiveCoupon(couponId: Long) {
        viewModelScope.launch {
            try {
                couponRepository.receiveCoupon(couponId)
                _receiveCouponState.value = RequestState.Success
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
        _getCouponListState.value = RequestState.Idle
    }
}
