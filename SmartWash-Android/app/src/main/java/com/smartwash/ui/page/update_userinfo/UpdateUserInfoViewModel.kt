package com.smartwash.ui.page.update_userinfo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.user.UpdateUserInfo
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.school.SchoolName
import com.smartwash.R
import com.smartwash.repository.SchoolRepository
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class UpdateUserInfoViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val userApi: UserApi,
) : ViewModel() {
    private val _schools = MutableStateFlow<List<SchoolName>>(emptyList())
    val schools = _schools.asStateFlow()
    private val _searchName = MutableStateFlow<String>("")
    val searchName = _searchName.asStateFlow()
    private val _updateState = MutableStateFlow<RequestState>(RequestState.Idle)
    val updateState = _updateState.asStateFlow()

    init {
        viewModelScope.launch {
            searchName
                .debounce(300)
                .collect { searchSchool() }
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
        viewModelScope.launch {
            if (checkStudentId(studentId)) {
                try {
                    userApi.updateUserInfo(UpdateUserInfo(schoolId, studentId))
                    _updateState.value = RequestState.Success
                } catch (e: NetworkException) {
                    Log.e(AppConstant.APP_NAME, "UpdateUserInfoViewModel.updateUserInfo: ${e.message}", e)
                    _updateState.value = RequestState.Error(e.resId, e.message)
                }
            } else {
                _updateState.value = RequestState.Error(R.string.error_student_id_registered)
            }
        }
    }

    fun searchSchool() {
        viewModelScope.launch {
            try {
                _schools.value = schoolRepository.getAllSchools(searchName.value)
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UpdateUserInfoViewModel.searchSchool: ${e.message}", e)
            }
        }
    }

    private suspend fun checkStudentId(studentId: String): Boolean {
        return try {
            val responseData = userApi.getUserByStudentId(studentId)
            responseData.data == true
        } catch (e: Exception) {
            Log.e(AppConstant.APP_NAME, "UpdateUserInfoViewModel.checkStudentId: ${e.message}", e)
            false
        }
    }
}
