package com.smartwash.feature.user.impl.ui.update_userinfo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.laundry.api.SchoolSearchSource
import com.smartwash.feature.laundry.api.model.SchoolOption
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.feature.user.impl.network.api.UserAccountApi
import com.smartwash.feature.user.impl.network.entity.user.UpdateUserInfo
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
    private val schoolSearch: SchoolSearchSource,
    private val userApi: UserAccountApi,
) : ViewModel() {
    private val _schools = MutableStateFlow<List<SchoolOption>>(emptyList())
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
                    Log.e(UserImplConstant.APP_NAME, "UpdateUserInfoViewModel.updateUserInfo: ${e.message}", e)
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
                _schools.value = schoolSearch.search(_searchName.value)
            } catch (e: NetworkException) {
                Log.e(UserImplConstant.APP_NAME, "UpdateUserInfoViewModel.searchSchool: ${e.message}", e)
            }
        }
    }

    private suspend fun checkStudentId(studentId: String): Boolean {
        return try {
            val responseData = userApi.getUserByStudentId(studentId)
            responseData.data == true
        } catch (e: Exception) {
            Log.e(UserImplConstant.APP_NAME, "UpdateUserInfoViewModel.checkStudentId: ${e.message}", e)
            false
        }
    }
}
