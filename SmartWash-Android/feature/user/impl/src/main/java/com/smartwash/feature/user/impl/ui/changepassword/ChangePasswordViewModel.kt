package com.smartwash.feature.user.impl.ui.changepassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
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
class ChangePasswordViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
) : ViewModel() {
    private val _changePasswordState = MutableStateFlow<RequestState>(RequestState.Idle)
    val changePasswordState = _changePasswordState.asStateFlow()

    /**
     * 修改密码：成功后清空本地会话（access + refreshToken）并广播登录失效，
     * 壳层统一跳登录页（改密后旧会话凭据作废，强制重新登录）。
     */
    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                _changePasswordState.value = RequestState.Loading
                userRepository.changePassword(oldPassword, newPassword)
                AppLogger.info(TAG, "密码修改成功")
                _changePasswordState.value = RequestState.Success
                // 清 access + refresh 双 token（幂等），再广播登录失效（壳层回登录页）
                sessionManager.clearToken()
                sessionEventBus.notifyUnauthorized()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                // 原密码不正确等业务失败经 ResponseInterceptor 转译，message 为后端文案
                AppLogger.error(TAG, "ChangePasswordViewModel.changePassword: ${e.message}", e)
                _changePasswordState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun setChangePasswordIdle() {
        _changePasswordState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "ChangePasswordViewModel"
    }
}
