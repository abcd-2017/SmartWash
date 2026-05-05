package com.smartwash.ui.page.coupon

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.database.dao.CouponVoDao
import com.smartwash.database.entity.CouponVoEntity
import com.smartwash.network.api.CouponApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.paging.UserCouponPagingSource
import com.smartwash.paging.pagingFlow
import com.smartwash.R
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponApi: CouponApi,
    private val couponVoDao: CouponVoDao,
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
            // 1. 读取本地缓存
            val cached = couponVoDao.getAll().map { it.toVo() }
            if (cached.isNotEmpty()) {
                _couponList.value = cached
            } else {
                _getCouponListState.value = RequestState.Loading
            }

            // 2. 后台请求网络
            try {
                val responseData = couponApi.getAllCoupon()
                val networkData = responseData.data ?: emptyList()
                _couponList.value = networkData
                _getCouponListState.value = RequestState.Success
                // 3. 更新缓存
                couponVoDao.deleteAll()
                couponVoDao.insertAll(networkData.map { CouponVoEntity.fromVo(it) })
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "CouponViewModel.getCouponList: ${e.message}", e)
                // 4. 仅在无缓存时显示错误
                if (cached.isEmpty()) {
                    _getCouponListState.value = RequestState.Error(e.resId, e.message)
                }
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
