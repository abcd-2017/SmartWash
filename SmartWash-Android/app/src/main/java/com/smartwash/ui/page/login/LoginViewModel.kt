package com.smartwash.ui.page.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.user.LoginUser
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.utils.RequestState
import com.smartwash.utils.SharePreferenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userApi: UserApi
) : ViewModel() {
    private val _loginState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loginState = _loginState.asStateFlow()

    fun getCaptcha() {
        viewModelScope.launch(Dispatchers.IO) {
            val captcha = userApi.getCaptcha("13444444444")
            println(captcha)
        }
    }

    fun loginUser(phoneNumber: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = RequestState.Loading
            try {
                val responseData = userApi.login(LoginUser(phoneNumber, password))
                withContext(Dispatchers.Main) {
                    _loginState.value = RequestState.Success
                    SharePreferenceUtils.saveData(AppConstant.TOKEN, responseData.data)
                }
            } catch (e: NetworkException) {
                withContext(Dispatchers.Main) {
                    _loginState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = RequestState.Idle
    }
}