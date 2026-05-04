package com.smartwash.ui.page.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.user.RegisterUser
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.utils.HttpStatusCode
import com.smartwash.R
import com.smartwash.utils.RequestState
import com.smartwash.utils.SharePreferenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val userApi: UserApi,
) : ViewModel() {
    private val _registerState = MutableStateFlow<RequestState>(RequestState.Idle)
    val registerState = _registerState.asStateFlow()
    private val _captchaState = MutableStateFlow<RequestState>(RequestState.Idle)
    val captchaState = _captchaState.asStateFlow()
    private val _captchaValue = MutableStateFlow<String>("")
    val captchaValue = _captchaValue.asStateFlow()

    fun getCaptcha(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _captchaState.value = RequestState.Loading
                val responseData = userApi.getCaptcha(phoneNumber)

                if (responseData.code == HttpStatusCode.Success.code && responseData.data != null && responseData.data.length == 6) {
                    _captchaValue.value = responseData.data
                    _captchaState.value = RequestState.Success
                } else {
                    _captchaState.value = RequestState.Error(R.string.error_get_captcha_failed)
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "RegisterViewModel.getCaptcha: ${e.message}", e)
                _captchaState.value = RequestState.Error(e.resId)
            }
        }
    }

    fun userRegister(phoneNumber: String, password: String, captcha: String) {
        viewModelScope.launch {
            try {
                _registerState.value = RequestState.Loading
                val registerUser = RegisterUser(phoneNumber, password, captcha)
                val responseData = userApi.register(registerUser)

                _registerState.value = RequestState.Success
                responseData.data?.let { SharePreferenceUtils.saveData(AppConstant.TOKEN, it) }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "RegisterViewModel.userRegister: ${e.message}", e)
                _registerState.value = RequestState.Error(e.resId)
            }
        }
    }

    fun setRegisterIdle() {
        _registerState.value = RequestState.Idle
    }

    fun setCaptchaIdle() {
        _captchaState.value = RequestState.Idle
    }

    fun setCaptchaLoading() {
        _captchaState.value = RequestState.Loading
    }
}