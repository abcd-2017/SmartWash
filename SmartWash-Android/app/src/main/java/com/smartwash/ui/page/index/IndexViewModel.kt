package com.smartwash.ui.page.index

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppConstant
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.model.UserInfo
import com.smartwash.R
import com.smartwash.common.utils.model.RequestState
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
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo = _userInfo.asStateFlow()
    private val _orderList = MutableStateFlow<List<OrderBrief>>(emptyList())
    val orderList = _orderList.asStateFlow()

    fun getInfoData() {
        viewModelScope.launch {
            _userInfoStatus.value = RequestState.Loading
            try {
                // UserApi.getUserInfo：登录后首次走网络、之后命中内存缓存（T5.3 起替代
                // 每页各自经 UserRepository 网络拉取）；未登录/失败返回 null，页面按空数据兜底
                _userInfo.value = userApi.getUserInfo()
                _orderList.value = orderApi.getWashingOrder()
                _userInfoStatus.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "IndexViewModel.getInfoData: ${e.message}", e)
                _userInfoStatus.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetState() {
        _userInfoStatus.value = RequestState.Idle
    }
}
