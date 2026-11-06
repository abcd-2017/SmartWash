package com.smartwash.feature.coupon.ui.coupon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.coupon.repository.CouponPageData
import com.smartwash.feature.coupon.repository.CouponRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponRepository: CouponRepository,
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

    /** 三组数据的降级来源标记（A5）：UI 据此区分正常/离线缓存/失败小态，不再把降级伪装成空态 */
    private val _loadSources = MutableStateFlow<CouponPageData?>(null)
    val loadSources = _loadSources.asStateFlow()

    fun loadAllCoupons() {
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                // 经 Repository 访问数据（含缓存降级链路），VM 不直接持有 API
                val data = couponRepository.getAllCoupons()
                _availableCoupons.value = data.available.items
                _claimedCoupons.value = data.claimed.items
                _historicalCoupons.value = data.historical.items
                _loadSources.value = data
                _loadState.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "CouponViewModel.loadAllCoupons: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun receiveCoupon(couponId: Long) {
        viewModelScope.launch {
            try {
                couponRepository.receiveCoupon(couponId)
                // 领取成功 = 低频关键动作成功，info 级（couponId 为业务键，无敏感信息）
                AppLogger.info(TAG, "领取优惠券成功，couponId=$couponId")
                _receiveCouponState.value = RequestState.Success
                loadAllCoupons()
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "CouponViewModel.receiveCoupon: ${e.message}", e)
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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "CouponViewModel"
    }
}
