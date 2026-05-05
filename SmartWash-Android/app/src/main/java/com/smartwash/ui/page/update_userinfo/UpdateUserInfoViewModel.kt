package com.smartwash.ui.page.update_userinfo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.database.dao.SchoolNameDao
import com.smartwash.database.entity.SchoolNameEntity
import com.smartwash.network.api.SchoolApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.user.UpdateUserInfo
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.school.SchoolName
import com.smartwash.R
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
    private val schoolApi: SchoolApi,
    private val userApi: UserApi,
    private val schoolNameDao: SchoolNameDao,
) : ViewModel() {
    private val _schools = MutableStateFlow<List<SchoolName>>(emptyList())
    val schools = _schools.asStateFlow()
    private val _searchName = MutableStateFlow<String>("")
    val searchName = _searchName.asStateFlow()
    private val _updateState = MutableStateFlow<RequestState>(RequestState.Idle)
    val updateState = _updateState.asStateFlow()
    private var allSchools: List<SchoolName> = emptyList()

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
            // 1. 如果内存中已有完整列表，直接本地过滤
            if (allSchools.isNotEmpty()) {
                _schools.value = if (searchName.value.isBlank()) {
                    allSchools
                } else {
                    allSchools.filter {
                        it.schoolName.contains(searchName.value, ignoreCase = true)
                    }
                }
                return@launch
            }

            // 2. 尝试从数据库加载
            val cached = schoolNameDao.getAll().map { it.toVo() }
            if (cached.isNotEmpty()) {
                allSchools = cached
                _schools.value = if (searchName.value.isBlank()) {
                    cached
                } else {
                    cached.filter {
                        it.schoolName.contains(searchName.value, ignoreCase = true)
                    }
                }
                // 仍然后台刷新完整列表
                refreshSchoolListFromNetwork()
                return@launch
            }

            // 3. 缓存为空，从网络加载
            try {
                val responseData = schoolApi.getAllSchool(searchName.value)
                val networkData = responseData.data.orEmpty()
                _schools.value = networkData

                // 如果是空搜索，同步缓存完整列表
                if (searchName.value.isBlank()) {
                    allSchools = networkData
                    schoolNameDao.deleteAll()
                    schoolNameDao.insertAll(networkData.map { SchoolNameEntity.fromVo(it) })
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UpdateUserInfoViewModel.searchSchool: ${e.message}", e)
            }
        }
    }

    private fun refreshSchoolListFromNetwork() {
        viewModelScope.launch {
            try {
                val responseData = schoolApi.getAllSchool("")
                val networkData = responseData.data.orEmpty()
                allSchools = networkData
                // 如果当前没有搜索关键词，同步更新 UI
                if (searchName.value.isBlank()) {
                    _schools.value = networkData
                }
                // 更新缓存
                schoolNameDao.deleteAll()
                schoolNameDao.insertAll(networkData.map { SchoolNameEntity.fromVo(it) })
            } catch (e: NetworkException) {
                // 后台刷新失败静默处理，缓存数据仍可用
                Log.e(AppConstant.APP_NAME, "UpdateUserInfoViewModel.refreshSchoolList: ${e.message}", e)
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
