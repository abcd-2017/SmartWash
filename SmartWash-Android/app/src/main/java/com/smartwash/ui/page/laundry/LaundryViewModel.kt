package com.smartwash.ui.page.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.laundry.LaundryItem
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LaundryViewModel @Inject constructor(
    private val laundryItemsApi: LaundryItemsApi,
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
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()

    fun getLaundryItem() {
        _getLaundryItemState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val responseData = laundryItemsApi.getLaundryItems()
                val userInfoRes = userApi.getUserInfo()
                _laundryItems.value = responseData.data ?: emptyList()
                _userInfo.value = userInfoRes.data
                _getLaundryItemState.value = RequestState.Success
            } catch (e: NetworkException) {
                _getLaundryItemState.value = RequestState.Error(e.message ?: "获取洗衣项目失败")
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
                val responseData =
                    orderApi.reservationLaundry(ReservationLaundry(selectItemId, totalPrice))
                if (responseData.data != null) {
                    _reservationState.value = RequestState.Success
                    _orderId.value = responseData.data
                } else {
                    _reservationState.value = RequestState.Error("预约失败")
                }
            } catch (e: NetworkException) {
                _reservationState.value = RequestState.Error(e.message ?: "预约失败")
            }
        }
    }

    fun resetReservationState() {
        _reservationState.value = RequestState.Idle
    }
}