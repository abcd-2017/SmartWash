package com.smartwash.feature.user.impl.ui.userinfo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.vo.user.UserInfoVo
import com.smartwash.feature.user.impl.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class UserInfoViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val application: Application,
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()
    private val _bindCampusState = MutableStateFlow<RequestState>(RequestState.Idle)
    val bindCampusState = _bindCampusState.asStateFlow()
    private val _unBindCampusState = MutableStateFlow<RequestState>(RequestState.Idle)
    val unBindCampusState = _unBindCampusState.asStateFlow()
    private val _avatarUploadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val avatarUploadState = _avatarUploadState.asStateFlow()

    fun getUserInfo() {
        viewModelScope.launch {
            _userInfoStatus.value = RequestState.Loading
            try {
                _userInfo.value = userRepository.getUserInfo()
                _userInfoStatus.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "UserInfoViewModel.getUserInfo: ${e.message}", e)
                _userInfoStatus.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetState() {
        _userInfoStatus.value = RequestState.Idle
    }

    fun resetBindCampusState() {
        _bindCampusState.value = RequestState.Idle
    }

    fun resetUnBindCampusState() {
        _unBindCampusState.value = RequestState.Idle
    }

    fun bindCampus(campusCard: String) {
        _bindCampusState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                if (userRepository.bindCampus(campusCard)) {
                    getUserInfo()
                    // 低频关键动作成功
                    AppLogger.info(TAG, "校园卡绑定成功")
                    _bindCampusState.value = RequestState.Success
                } else {
                    // 业务拒绝：后端未确认绑定（data 非 true）
                    AppLogger.warn(TAG, "校园卡绑定被后端拒绝")
                }
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "UserInfoViewModel.bindCampus: ${e.message}", e)
                _bindCampusState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun unBindCampus() {
        _unBindCampusState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                if (userRepository.unBindCampus()) {
                    getUserInfo()
                    // 低频关键动作成功
                    AppLogger.info(TAG, "校园卡解绑成功")
                    _unBindCampusState.value = RequestState.Success
                } else {
                    // 业务拒绝：后端未确认解绑（data 非 true）
                    AppLogger.warn(TAG, "校园卡解绑被后端拒绝")
                }
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "UserInfoViewModel.unBindCampus: ${e.message}", e)
                _unBindCampusState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        _avatarUploadState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val inputStream = application.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException(application.getString(R.string.avatar_upload_failed))
                val bytes = inputStream.use { it.readBytes() }
                val mimeType = application.contentResolver.getType(uri) ?: "image/jpeg"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "avatar.jpg", requestBody)
                userRepository.uploadAvatar(part)
                getUserInfo()
                // 低频关键动作成功（资料变更）
                AppLogger.info(TAG, "头像上传成功 size=${bytes.size}B")
                _avatarUploadState.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "UserInfoViewModel.uploadAvatar: ${e.message}", e)
                _avatarUploadState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "UserInfoViewModel.uploadAvatar: ${e.message}", e)
                _avatarUploadState.value = RequestState.Error(R.string.avatar_upload_failed, e.message)
            }
        }
    }

    fun resetAvatarUploadState() {
        _avatarUploadState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "UserInfoViewModel"
    }
}
