package com.smartwash.feature.user.impl.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.feature.user.impl.network.entity.user.LoginUser
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
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
    private val userApi: UserApi,
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
                    Log.e(UserImplConstant.APP_NAME, "LoginViewModel.loginUser: 登录响应数据为空")
                    _loginState.value = RequestState.Error(R.string.error_login_failed)
                    return@launch
                }
                // 经 SessionManager 保存：token 与 role 的内存缓存和 DataStore 同步更新
                sessionManager.saveToken(token, loginVo.role)
                // 会话建立广播（UserApiImpl 桥接为 LoginEvent.LoggedIn，并使用户信息缓存失效）
                sessionEventBus.notifyLoggedIn()
                _loginState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(UserImplConstant.APP_NAME, "LoginViewModel.loginUser: ${e.message}", e)
                _loginState.value = RequestState.Error(R.string.error_login_failed, e.message)
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = RequestState.Idle
    }

    /**
     * 是否已有持久化会话（登录页「已有 token 直接进首页」判断，T5.3 自页面参数
     * 切入移入）：suspend 读取优先命中内存缓存，未命中读 DataStore。
     */
    suspend fun hasSavedToken(): Boolean = sessionManager.getToken().isNotBlank()

    /**
     * 检查当前用户学校信息是否缺失。
     * true = 确认未填写（需强制补填）；false = 已填写；null = 无法确认（未登录或网络失败）。
     * 网络失败不误判为未填写——避免已填写用户在网络波动时被错误带到填写页。
     */
    suspend fun schoolInfoMissing(): Boolean? {
        return try {
            userApi.getUserInfo()?.let {
                it.school.schoolId <= 0L || it.studentId.isBlank()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(UserImplConstant.APP_NAME, "LoginViewModel.schoolInfoMissing: ${e.message}", e)
            null
        }
    }
}
