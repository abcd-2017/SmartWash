package com.smartwash.ui.page.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.order.OrderVo
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IndexViewModel @Inject constructor(
    private val userApi: UserApi,
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()
    private val _orderList = MutableStateFlow<List<OrderVo>>(emptyList())
    val orderList = _orderList.asStateFlow()

    fun getInfoData() {
        viewModelScope.launch {
            try {
                val responseData = userApi.getUserInfo()
                val washingOrderRes = orderApi.getWashingOrder()
                _userInfo.value = responseData.data
                _orderList.value = washingOrderRes.data ?: emptyList()
                _userInfoStatus.value = RequestState.Success
            } catch (e: NetworkException) {
                _userInfoStatus.value = RequestState.Error(e.message ?: "获取数据失败")
            }
        }
    }

    fun resetState() {
        _userInfoStatus.value = RequestState.Idle
    }
}