package com.smartwash.ui.page.login

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.RetrofitClient
import com.smartwash.network.api.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {
    private lateinit var userApi: UserApi
    val captcha = mutableStateOf("")

    fun initParam(content: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            userApi = RetrofitClient(content).getRequestApi(UserApi::class.java)
        }

    }

    fun getCaptcha() {
        viewModelScope.launch(Dispatchers.IO) {
            val captcha = userApi.getCaptcha("13444444444")
            print("11111111111111111111$captcha")
        }
    }
}