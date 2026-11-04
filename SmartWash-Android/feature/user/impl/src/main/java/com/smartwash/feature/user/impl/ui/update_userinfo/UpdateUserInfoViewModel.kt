package com.smartwash.feature.user.impl.ui.update_userinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.laundry.api.SchoolSearchSource
import com.smartwash.feature.laundry.api.model.SchoolOption
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.impl.network.api.UserAccountApi
import com.smartwash.feature.user.impl.network.entity.user.UpdateUserInfo
import com.smartwash.feature.user.impl.network.vo.user.UserInfoVo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val userApiFacade: UserApi,
) : ViewModel() {
    private val _schools = MutableStateFlow<List<SchoolOption>>(emptyList())
    val schools = _schools.asStateFlow()
    private val _searchName = MutableStateFlow<String>("")
    val searchName = _searchName.asStateFlow()
    private val _updateState = MutableStateFlow<RequestState>(RequestState.Idle)
    val updateState = _updateState.asStateFlow()

    /**
     * 当前用户信息（进入页面时拉取一次）：已填写学校/学号的用户据此回填并进入
     * 只读态——学校/学号一经填写只能由管理员修改，不能在本页反复变更。
     */
    private val _currentInfo = MutableStateFlow<UserInfoVo?>(null)
    val currentInfo = _currentInfo.asStateFlow()

    init {
        viewModelScope.launch {
            searchName
                .debounce(300)
                .collect { searchSchool() }
        }
        loadCurrentInfo()
    }

    private fun loadCurrentInfo() {
        viewModelScope.launch {
            _currentInfo.value = try {
                userApi.getUserInfo().data
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "UpdateUserInfoViewModel.loadCurrentInfo: ${e.message}", e)
                null
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
        viewModelScope.launch {
            if (checkStudentId(studentId)) {
                try {
                    userApi.updateUserInfo(UpdateUserInfo(schoolId, studentId))
                    // 必须在置 Success（触发页面导航回首页）之前同步失效缓存：
                    // 首页的学校绑定引导走 UserApi.getUserInfo 内存缓存，若缓存仍是
                    // 提交前的"未填写"旧数据，会立刻把用户弹回本页（页面此时已被
                    // 锁定，表现为"填完被弹回且全部禁用"）
                    userApiFacade.invalidateUserInfoCache()
                    // 低频关键动作成功（资料保存成功）
                    AppLogger.info(TAG, "学校信息保存成功 schoolId=$schoolId")
                    _updateState.value = RequestState.Success
                } catch (e: NetworkException) {
                    AppLogger.error(TAG, "UpdateUserInfoViewModel.updateUserInfo: ${e.message}", e)
                    _updateState.value = RequestState.Error(e.resId, e.message)
                }
            } else {
                // 业务拒绝：学号已被占用（checkStudentId 校验不过）
                AppLogger.warn(TAG, "学号已被占用，学校信息保存被拒")
                _updateState.value = RequestState.Error(R.string.error_student_id_registered)
            }
        }
    }

    fun searchSchool() {
        viewModelScope.launch {
            try {
                _schools.value = schoolSearch.search(_searchName.value)
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "UpdateUserInfoViewModel.searchSchool: ${e.message}", e)
            }
        }
    }

    private suspend fun checkStudentId(studentId: String): Boolean {
        return try {
            val responseData = userApi.getUserByStudentId(studentId)
            responseData.data == true
        } catch (e: Exception) {
            AppLogger.error(TAG, "UpdateUserInfoViewModel.checkStudentId: ${e.message}", e)
            false
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "UpdateUserInfoViewModel"
    }
}
