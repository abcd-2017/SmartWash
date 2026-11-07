package com.smartwash.feature.user.impl.ui.resetpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.repository.UserRepository
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
) : ViewModel() {
    private val _captchaState = MutableStateFlow<RequestState>(RequestState.Idle)
    val captchaState = _captchaState.asStateFlow()

    /** 验证码接口 data 原文（本地 stub 模式含「本地演示验证码：{code}」，页面 toast 展示） */
    private val _captchaMessage = MutableStateFlow<String?>(null)
    val captchaMessage = _captchaMessage.asStateFlow()

    private val _resetState = MutableStateFlow<RequestState>(RequestState.Idle)
    val resetState = _resetState.asStateFlow()

    fun getCaptcha(phoneNumber: String) {
        viewModelScope.launch {
            try {
                _captchaState.value = RequestState.Loading
                val responseData = userRepository.getCaptcha(phoneNumber)
                if (responseData.code == HttpStatusCode.Success.code) {
                    // 验证码发送状态事件（只打状态，验证码值本身绝不入日志）
                    AppLogger.debug(TAG, "验证码发送成功（倒计时启动）")
                    // data 原文透传给页面 toast（本地 stub 模式含演示验证码；正式短信为空则回退默认文案）
                    _captchaMessage.value = responseData.data?.takeIf { it.isNotBlank() }
                    _captchaState.value = RequestState.Success
                } else {
                    // 业务拒绝（后端非成功码，如发送频率限制）
                    AppLogger.warn(TAG, "验证码发送被拒绝 code=${responseData.code}")
                    _captchaState.value = RequestState.Error(R.string.error_get_captcha_failed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "ResetPasswordViewModel.getCaptcha: ${e.message}", e)
                _captchaState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /**
     * 重置密码（忘记密码链路，免鉴权）：成功后清空本地会话（access + refreshToken）并
     * 广播登录失效，壳层统一跳登录页（本页由登录页压栈，回退即登录页）。
     */
    fun resetPassword(phoneNumber: String, code: String, newPassword: String) {
        viewModelScope.launch {
            try {
                _resetState.value = RequestState.Loading
                userRepository.resetPassword(phoneNumber, code, newPassword)
                AppLogger.info(TAG, "密码重置成功 phone=${AppLogger.maskPhone(phoneNumber)}")
                _resetState.value = RequestState.Success
                // 清 access + refresh 双 token（幂等），再广播登录失效（壳层 pop 回登录页）
                sessionManager.clearToken()
                sessionEventBus.notifyUnauthorized()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "ResetPasswordViewModel.resetPassword: ${e.message}", e)
                _resetState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun setResetIdle() {
        _resetState.value = RequestState.Idle
    }

    fun setCaptchaIdle() {
        _captchaState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "ResetPasswordViewModel"
    }
}
