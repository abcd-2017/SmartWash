package com.smartwash.feature.user.impl.ui.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.feature.user.impl.network.entity.user.RegisterUser
import com.smartwash.feature.user.impl.repository.UserRepository
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
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
                Log.e(UserImplConstant.APP_NAME, "RegisterViewModel.getCaptcha: ${e.message}", e)
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
                // 经 SessionManager 保存：内存缓存与 DataStore 同步更新
                sessionManager.saveToken(token)
                // 会话建立广播（注册即登录，UserApiImpl 桥接为 LoginEvent.LoggedIn）
                sessionEventBus.notifyLoggedIn()
            } catch (e: NetworkException) {
                Log.e(UserImplConstant.APP_NAME, "RegisterViewModel.userRegister: ${e.message}", e)
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
