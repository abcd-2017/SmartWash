package com.smartwash.ui.page.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.entity.user.LoginUser
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.session.SessionManager
import com.smartwash.utils.AppConstant
import com.smartwash.R
import com.smartwash.repository.UserRepository
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
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
                    Log.e(AppConstant.APP_NAME, "LoginViewModel.loginUser: 登录响应数据为空")
                    _loginState.value = RequestState.Error(R.string.error_login_failed)
                    return@launch
                }
                // 经 SessionManager 保存：token 与 role 的内存缓存和 DataStore 同步更新
                sessionManager.saveToken(token, loginVo.role)
                _loginState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "LoginViewModel.loginUser: ${e.message}", e)
                _loginState.value = RequestState.Error(R.string.error_login_failed, e.message)
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = RequestState.Idle
    }
}
