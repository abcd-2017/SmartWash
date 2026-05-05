package com.smartwash.ui.page.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.entity.user.RegisterUser
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.utils.HttpStatusCode
import com.smartwash.R
import com.smartwash.repository.UserRepository
import com.smartwash.utils.RequestState
import com.smartwash.utils.SharePreferenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _registerState = MutableStateFlow<RequestState>(RequestState.Idle)
    val registerState = _registerState.asStateFlow()
    private val _captchaState = MutableStateFlow<RequestState>(RequestState.Idle)
    val captchaState = _captchaState.asStateFlow()

    fun getCaptcha(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _captchaState.value = RequestState.Loading
                val responseData = userRepository.getCaptcha(phoneNumber)
                if (responseData.code == HttpStatusCode.Success.code) {
                    _captchaState.value = RequestState.Success
                } else {
                    _captchaState.value = RequestState.Error(R.string.error_get_captcha_failed)
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "RegisterViewModel.getCaptcha: ${e.message}", e)
                _captchaState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun userRegister(phoneNumber: String, password: String, captcha: String) {
        viewModelScope.launch {
            try {
                _registerState.value = RequestState.Loading
                val token = userRepository.register(RegisterUser(phoneNumber, password, captcha))
                _registerState.value = RequestState.Success
                SharePreferenceUtils.saveData(AppConstant.TOKEN, token)
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "RegisterViewModel.userRegister: ${e.message}", e)
                _registerState.value = RequestState.Error(e.resId, e.message)
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
