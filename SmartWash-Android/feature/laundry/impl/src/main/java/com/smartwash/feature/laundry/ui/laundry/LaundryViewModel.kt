package com.smartwash.feature.laundry.ui.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.laundry.R
import com.smartwash.feature.laundry.network.vo.LaundryItem
import com.smartwash.feature.laundry.repository.LaundryRepository
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.model.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LaundryViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
    private val orderApi: OrderApi,
    private val userApi: UserApi,
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()
    private val _reservationState = MutableStateFlow<RequestState>(RequestState.Idle)
    val reservationState = _reservationState.asStateFlow()
    private val _orderId = MutableStateFlow<Long>(-1)
    val orderId = _orderId.asStateFlow()
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo = _userInfo.asStateFlow()

    fun getLaundryItem() {
        viewModelScope.launch {
            // 有缓存时先显示缓存，无缓存时显示 Loading（缓存读取统一走 Repository，VM 不直接持有 DAO）
            val cached = laundryRepository.getCachedLaundryItems()
            if (cached.isNotEmpty()) {
                _laundryItems.value = cached
            } else {
                _getLaundryItemState.value = RequestState.Loading
            }

            try {
                val result = laundryRepository.getLaundryItems()
                _laundryItems.value = result
                _getLaundryItemState.value = RequestState.Success
                // 预约页为低频入口（非 Tab/渲染级高频路径）：加载完成打 info
                AppLogger.info(TAG, "getLaundryItem: 洗衣项目加载完成, size=${result.size}")
                // UserApi.getUserInfo：登录后首次走网络、之后命中内存缓存（T5.3）；
                // 未登录/失败返回 null，页面按空数据兜底
                _userInfo.value = userApi.getUserInfo()
                if (_userInfo.value == null) {
                    // 空态预警：预约页学校卡片将展示为空，用户可感知
                    AppLogger.warn(TAG, "getLaundryItem: 用户信息为空（未登录或获取失败），学校信息按空数据兜底")
                }
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "LaundryViewModel.getLaundryItem: ${e.message}", e)
                if (cached.isEmpty()) {
                    _getLaundryItemState.value = RequestState.Error(e.resId, e.message)
                }
            }
        }
    }

    fun resetGetLaundryItemState() {
        _getLaundryItemState.value = RequestState.Idle
    }

    fun reservationLaundry(selectItemId: Long, totalPrice: Float) {
        _reservationState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                // OrderApi 内 data 为 null 返回 -1，按预约失败处理
                val orderId = orderApi.reservationLaundry(selectItemId, totalPrice)
                if (orderId != -1L) {
                    // 低频关键动作成功：预约下单成功留痕（金额非敏感，可审计）
                    AppLogger.info(TAG, "reservationLaundry: 预约下单成功, orderId=$orderId, itemId=$selectItemId, totalPrice=$totalPrice")
                    _reservationState.value = RequestState.Success
                    _orderId.value = orderId
                } else {
                    // 业务拒绝：后端未受理（data 为 null），与网络异常区分开留痕
                    AppLogger.warn(TAG, "reservationLaundry: 预约未受理（后端未返回订单）, itemId=$selectItemId, totalPrice=$totalPrice")
                    _reservationState.value = RequestState.Error(R.string.error_booking_failed)
                }
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "LaundryViewModel.reservationLaundry: ${e.message}", e)
                _reservationState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetReservationState() {
        _reservationState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "LaundryViewModel"
    }
}
