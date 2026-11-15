package com.smartwash.lite.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.HttpStatusCode
import com.smartwash.common.utils.model.RequestState
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

/**
 * lite 注册 ViewModel（flavor 源集专属）。
 *
 * 与 full 的 `:feature:user:impl` RegisterViewModel 能力等价（验证码下发 + 注册即登录 +
 * 会话落盘 + 登录态广播），差异仅在去向：lite 注册成功后进 lite 首页，
 * 不走 full 的「学校信息补填」页（lite 无学校域）。
 *
 * 单独实现而非复用 full VM：full 的 RegisterViewModel/RegisterPage 已随 full 源集下沉，
 * lite classpath 不存在该类型（同 [LiteLoginViewModel] 的处理）。
 */
@HiltViewModel
class LiteRegisterViewModel @Inject constructor(
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
                AppLogger.error(TAG, "LiteRegisterViewModel.getCaptcha: ${e.message}", e)
                _captchaState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun userRegister(phoneNumber: String, password: String, captcha: String) {
        viewModelScope.launch {
            try {
                _registerState.value = RequestState.Loading
                // 注册即自动登录：响应为 LoginVo 对象（token/refreshToken/role），与登录同构
                val loginVo = userRepository.register(RegisterUser(phoneNumber, password, captcha))
                val token = loginVo.token
                if (token.isNullOrBlank()) {
                    // 响应 data/token 为空时按失败处理：不落空 token、不置 Success
                    AppLogger.error(TAG, "LiteRegisterViewModel.userRegister: 注册响应数据为空")
                    _registerState.value = RequestState.Error(R.string.error_login_failed)
                    return@launch
                }
                _registerState.value = RequestState.Success
                sessionManager.saveToken(token, loginVo.role, loginVo.refreshToken)
                sessionEventBus.notifyLoggedIn()
                // 低频关键动作成功（手机号必须脱敏后才可入日志）
                AppLogger.info(TAG, "注册成功 phone=${AppLogger.maskPhone(phoneNumber)}")
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "LiteRegisterViewModel.userRegister: ${e.message}", e)
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

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "LiteRegisterViewModel"
    }
}
