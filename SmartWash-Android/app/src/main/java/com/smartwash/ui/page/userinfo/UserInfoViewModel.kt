package com.smartwash.ui.page.userinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.UserApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UserInfoViewModel @Inject constructor(
    private val userApi: UserApi
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()

    fun getUserInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = userApi.getUserInfo()
                withContext(Dispatchers.Main) {
                    _userInfo.value = responseData.data
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