package com.smartwash.ui.page.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.user.RegisterUser
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.utils.HttpStatusCode
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
class RegisterViewModel @Inject constructor(
    private val userApi: UserApi,
) : ViewModel() {
    private val _registerState = MutableStateFlow<RequestState>(RequestState.Idle)
    val registerState = _registerState.asStateFlow()
    private val _captchaState = MutableStateFlow<CaptchaState>(CaptchaState.Idle)
    val captchaState = _captchaState.asStateFlow()

    fun getCaptcha(phoneNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _captchaState.value = CaptchaState.Loading
                val responseData = userApi.getCaptcha(phoneNumber)

                withContext(Dispatchers.Main) {
                    _captchaState.value =
                        if (responseData.code == HttpStatusCode.Success.code && responseData.data != null && responseData.data.length == 6) {
                            CaptchaState.Success("${responseData.data}")
                        } else CaptchaState.Error("${responseData.data}")//验证码获取失败
                    Log.e("smart wash", "getCaptcha: $responseData")
                }
            } catch (e: NetworkException) {
                withContext(Dispatchers.Main) {
                    _captchaState.value = CaptchaState.Error("${e.message}")
                }
            }
        }
    }

    fun userRegister(phoneNumber: String, password: String, captcha: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _registerState.value = RequestState.Loading
                val registerUser = RegisterUser(phoneNumber, password, captcha)
                val responseData = userApi.register(registerUser)

                withContext(Dispatchers.Main) {
                    _registerState.value = RequestState.Success
                    SharePreferenceUtils.saveData(AppConstant.TOKEN, responseData.data)
                }
            } catch (e: NetworkException) {
                withContext(Dispatchers.Main) {
                    _captchaState.value = CaptchaState.Error("${e.message}")
                }
            }
        }
    }

    fun setRegisterIdle() {
        _registerState.value = RequestState.Idle
    }

    fun setCaptchaIdle() {
        _captchaState.value = CaptchaState.Idle
    }

    fun setCaptchaLoading() {
        _captchaState.value = CaptchaState.Loading
    }
}

sealed class CaptchaState {
    data object Idle : CaptchaState()
    data object Loading : CaptchaState()
    data class Success(val message: String) : CaptchaState()
    data class Error(val message: String) : CaptchaState()
}
