package com.smartwash.lite.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.entity.user.LoginUser
import com.smartwash.feature.user.impl.repository.UserRepository
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * lite 登录 ViewModel（flavor 源集专属）。
 *
 * 与 full 的 `:feature:user:impl` LoginViewModel 的差异：
 * - **不做学校信息补填判断**（lite 无学校域，登录成功 / 已有会话一律进 lite 首页）；
 * - 因此不注入 [com.smartwash.feature.user.api.UserApi]（full 版用它判断学校信息缺失）。
 *
 * 会话链路完全复用 user-impl 的 [UserRepository] / [SessionManager] / [SessionEventBus]，
 * 与 full 登录同一条落盘与广播路径（token 键不变，两变体登录态互通）。
 */
@HiltViewModel
class LiteLoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
) : ViewModel() {

    private val _loginState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loginState = _loginState.asStateFlow()

    fun loginUser(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _loginState.value = RequestState.Loading
            try {
                // 登录响应 data 为 {token, role} 对象（后端 LoginVo）
                val loginVo = userRepository.login(LoginUser(phoneNumber, password))
                val token = loginVo.token
                if (token.isNullOrBlank()) {
                    // 响应 data/token 为空时按失败处理：不落空 token、不置 Success，避免首页鉴权循环闪屏
                    AppLogger.error(TAG, "LiteLoginViewModel.loginUser: 登录响应数据为空")
                    _loginState.value = RequestState.Error(R.string.error_login_failed)
                    return@launch
                }
                sessionManager.saveToken(token, loginVo.role, loginVo.refreshToken)
                sessionEventBus.notifyLoggedIn()
                // 低频关键动作成功（手机号必须脱敏后才可入日志）
                AppLogger.info(TAG, "登录成功 phone=${AppLogger.maskPhone(phoneNumber)}")
                _loginState.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "LiteLoginViewModel.loginUser: ${e.message}", e)
                _loginState.value = RequestState.Error(R.string.error_login_failed, e.message)
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = RequestState.Idle
    }

    /** 是否已有持久化会话（登录页「已有 token 直接进首页」判断）：suspend 读，优先命中内存缓存 */
    suspend fun hasSavedToken(): Boolean = sessionManager.getToken().isNotBlank()

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "LiteLoginViewModel"
    }
}
