package com.smartwash.ui.page.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.R
import com.smartwash.common.model.RequestState
import com.smartwash.feature.user.api.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userApi: UserApi,
) : ViewModel() {
    private val _userSchoolId = MutableStateFlow<Long>(-1)
    val hasUserSchool = _userSchoolId.asStateFlow()
    private val _getSchoolState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getSchoolState = _getSchoolState.asStateFlow()

    fun getUserSchool() {
        _getSchoolState.value = RequestState.Loading

        viewModelScope.launch {
            // UserApi.getUserInfo 契约：未登录/获取失败返回 null（不抛异常）；未绑定
            // 学校的用户 schoolId 落 -1（UserApiImpl 映射默认值，对齐原 getUserSchoolId
            // 接口的空值语义）。null → Error 保持原「失败不引导绑定学校」的行为。
            val userInfo = userApi.getUserInfo()
            if (userInfo != null) {
                _userSchoolId.value = userInfo.school.schoolId
                _getSchoolState.value = RequestState.Success
            } else {
                _getSchoolState.value = RequestState.Error(R.string.error_network_fail)
            }
        }
    }
}
