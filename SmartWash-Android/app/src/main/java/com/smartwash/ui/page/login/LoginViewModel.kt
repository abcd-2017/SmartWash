package com.smartwash.ui.page.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.user.LoginUser
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.R
import com.smartwash.utils.RequestState
import com.smartwash.utils.SharePreferenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userApi: UserApi
) : ViewModel() {
    private val _loginState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loginState = _loginState.asStateFlow()

    fun loginUser(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _loginState.value = RequestState.Loading
            try {
                val responseData = userApi.login(LoginUser(phoneNumber, password))
                _loginState.value = RequestState.Success
                responseData.data?.let { SharePreferenceUtils.saveData(AppConstant.TOKEN, it) }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "LoginViewModel.loginUser: ${e.message}", e)
                _loginState.value = RequestState.Error(R.string.error_login_failed)
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = RequestState.Idle
    }
}