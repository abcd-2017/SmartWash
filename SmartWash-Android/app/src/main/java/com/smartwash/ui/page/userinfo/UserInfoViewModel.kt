package com.smartwash.ui.page.userinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.order.OrderItemCountFrom
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.order.OrderItemCountVo
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.utils.RequestState
import com.smartwash.utils.ShowOrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UserInfoViewModel @Inject constructor(
    private val userApi: UserApi,
    private val orderApi: OrderApi
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()
    private val _orderItemCount = MutableStateFlow<OrderItemCountVo?>(null)
    val orderItemCount = _orderItemCount.asStateFlow()

    fun getUserInfo() {
        viewModelScope.launch(Dispatchers.IO) {
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
                withContext(Dispatchers.Main) {
                    _userInfo.value = responseData.data
                    _orderItemCount.value = orderItemCountRes.data
                    _userInfoStatus.value = RequestState.Success
                }
            } catch (e: NetworkException) {
                withContext(Dispatchers.Main) {
                    _userInfoStatus.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    fun resetState() {
        _userInfoStatus.value = RequestState.Idle
    }
}