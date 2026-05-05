package com.smartwash.ui.page.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.entity.user.LoginUser
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
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
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _loginState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loginState = _loginState.asStateFlow()

    fun loginUser(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _loginState.value = RequestState.Loading
            try {
                val token = userRepository.login(LoginUser(phoneNumber, password))
                _loginState.value = RequestState.Success
                SharePreferenceUtils.saveData(AppConstant.TOKEN, token)
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
