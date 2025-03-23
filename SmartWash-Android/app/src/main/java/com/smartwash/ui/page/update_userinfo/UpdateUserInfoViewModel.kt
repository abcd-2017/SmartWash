package com.smartwash.ui.page.userinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.SchoolApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.user.UpdateUserInfo
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.SchoolName
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class UpdateUserInfoViewModel @Inject constructor(
    private val schoolApi: SchoolApi,
    private val userApi: UserApi
) : ViewModel() {
    private val _schools = MutableStateFlow<List<SchoolName>>(emptyList())
    val schools = _schools.asStateFlow()
    private val _searchName = MutableStateFlow<String>("")
    val searchName = _searchName.asStateFlow()
    private val _updateState = MutableStateFlow<RequestState>(RequestState.Idle)
    val updateState = _updateState.asStateFlow()
    private val _studentIdError = MutableStateFlow<Boolean>(false)
    private val studentIdError = _studentIdError.asStateFlow()

    init {
        //监听搜索查询关键字的变化
        viewModelScope.launch {
            searchName
                .debounce(300)// 防抖，避免频繁请求
                .collect {
                    searchSchool()
                }
        }
    }

    fun setStateIdle() {
        _updateState.value = RequestState.Idle
    }

    fun updateSearchName(searchName: String) {
        _searchName.value = searchName
    }

    fun updateUserInfo(schoolId: Long, studentId: String) {
        _updateState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            if (checkStudentId(studentId)) {
                try {
                    val responseData = userApi.updateUserInfo(UpdateUserInfo(schoolId, studentId))
                    withContext(Dispatchers.Main) {
                        _updateState.value = RequestState.Success
                    }
                } catch (e: NetworkException) {
                    withContext(Dispatchers.Main) {
                        _updateState.value = RequestState.Error("${e.message}")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _updateState.value = RequestState.Error("该学号已注册")
                }
            }
        }
    }

    fun searchSchool() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = schoolApi.getAllSchool(searchName.value)
                _schools.value = responseData.data.orEmpty()
            } catch (_: NetworkException) {
            }
        }
    }

    private suspend fun checkStudentId(studentId: String): Boolean {
        return try {
            val responseData = userApi.getUserByStudentId(studentId)
            responseData.data == true
        } catch (_: Exception) {
            false
        }
    }
}