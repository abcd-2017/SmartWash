package com.smartwash.feature.user.impl.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.user.impl.R
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
                    // 验证码发送状态事件（只打状态，验证码值本身绝不入日志）
                    AppLogger.debug(TAG, "验证码发送成功（倒计时启动）")
                    _captchaState.value = RequestState.Success
                } else {
                    // 业务拒绝（后端非成功码，如发送频率限制）
                    AppLogger.warn(TAG, "验证码发送被拒绝 code=${responseData.code}")
                    _captchaState.value = RequestState.Error(R.string.error_get_captcha_failed)
                }
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "RegisterViewModel.getCaptcha: ${e.message}", e)
                _captchaState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun userRegister(phoneNumber: String, password: String, captcha: String) {
        viewModelScope.launch {
            try {
                _registerState.value = RequestState.Loading
                // 注册即自动登录：响应已升级为 LoginVo 对象（token/refreshToken/role），与登录同构
                val loginVo = userRepository.register(RegisterUser(phoneNumber, password, captcha))
                val token = loginVo.token
                if (token.isNullOrBlank()) {
                    // 响应 data/token 为空时按失败处理：不落空 token、不置 Success
                    AppLogger.error(TAG, "RegisterViewModel.userRegister: 注册响应数据为空")
                    _registerState.value = RequestState.Error(R.string.error_login_failed)
                    return@launch
                }
                _registerState.value = RequestState.Success
                // 经 SessionManager 保存：token/role/refreshToken 内存缓存与 DataStore 同步更新
                sessionManager.saveToken(token, loginVo.role, loginVo.refreshToken)
                // 会话建立广播（注册即登录，UserApiImpl 桥接为 LoginEvent.LoggedIn）
                sessionEventBus.notifyLoggedIn()
                // 低频关键动作成功（手机号必须脱敏后才可入日志）
                AppLogger.info(TAG, "注册成功 phone=${AppLogger.maskPhone(phoneNumber)}")
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "RegisterViewModel.userRegister: ${e.message}", e)
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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "RegisterViewModel"
    }
}
