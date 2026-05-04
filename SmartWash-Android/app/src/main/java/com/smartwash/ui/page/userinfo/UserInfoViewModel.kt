package com.smartwash.ui.page.userinfo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.order.OrderItemCountFrom
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.order.OrderItemCountVo
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.R
import com.smartwash.utils.RequestState
import com.smartwash.utils.ShowOrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserInfoViewModel @Inject constructor(
    private val userApi: UserApi,
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()
    private val _orderItemCount = MutableStateFlow<OrderItemCountVo?>(null)
    val orderItemCount = _orderItemCount.asStateFlow()
    private val _bindCampusState = MutableStateFlow<RequestState>(RequestState.Idle)
    val bindCampusState = _bindCampusState.asStateFlow()
    private val _unBindCampusState = MutableStateFlow<RequestState>(RequestState.Idle)
    val unBindCampusState = _unBindCampusState.asStateFlow()

    fun getUserInfo() {
        viewModelScope.launch {
            try {
                val responseData = userApi.getUserInfo()
                val orderItemCountRes = orderApi.getOrderItemCount(
                    OrderItemCountFrom(
                        processingStatus = ShowOrderStatus.WASHING.status,
                        shippedStatus = ShowOrderStatus.PENDING_SHIPMENT.status,
                        pendingPickupStatus = ShowOrderStatus.READY_FOR_PICKUP.status,
                        pendingPaymentStatus = ShowOrderStatus.PENDING_PAYMENT.status
                    )
                )
                _userInfo.value = responseData.data
                _orderItemCount.value = orderItemCountRes.data
                _userInfoStatus.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.getUserInfo: ${e.message}", e)
                _userInfoStatus.value = RequestState.Error(e.resId)
            }
        }
    }

    fun resetState() {
        _userInfoStatus.value = RequestState.Idle
    }

    fun resetBindCampusState() {
        _bindCampusState.value = RequestState.Idle
    }

    fun resetUnBindCampusState() {
        _unBindCampusState.value = RequestState.Idle
    }

    fun bindCampus(campusCard: String) {
        _bindCampusState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val bindCampus = userApi.bindCampus(campusCard)
                if (bindCampus.data == true) {
                    getUserInfo()
                    _bindCampusState.value = RequestState.Success
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.bindCampus: ${e.message}", e)
                _bindCampusState.value = RequestState.Error(e.resId)
            }
        }
    }

    fun unBindCampus() {
        _unBindCampusState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val bindCampus = userApi.unBindCampus()
                if (bindCampus.data == true) {
                    getUserInfo()
                    _unBindCampusState.value = RequestState.Success
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.unBindCampus: ${e.message}", e)
                _unBindCampusState.value = RequestState.Error(e.resId)
            }
        }
    }
}