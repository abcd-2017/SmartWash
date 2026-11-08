package com.smartwash.feature.user.impl.ui.userinfo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.notice.api.NoticeApi
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.vo.user.UserInfoVo
import com.smartwash.feature.user.impl.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val noticeApi: NoticeApi,
    private val application: Application,
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()

    /**
     * 消息中心未读数（跨域经 notice-api 的 [NoticeApi]，impl → 他人 api 合法）。
     * 独立于用户信息主流程：失败仅留痕，角标隐藏，不影响我的页渲染。
     */
    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount = _unreadCount.asStateFlow()

    /**
     * 最近一次 getUserInfo 的失败载荷（回归 2026-11-06 A2）：
     * userInfoStatus 会被 resetState 回 Idle，页面无法据此区分「未加载/加载中」与「失败态」；
     * 本状态独立承载失败语义（null=无失败，非 null=携带具体文案的 Error）——无数据时驱动
     * 错误卡（替代归零假数据卡），有数据时驱动离线提示条，直到下一次成功拉取清除。
     */
    private val _lastError = MutableStateFlow<RequestState.Error?>(null)
    val lastError = _lastError.asStateFlow()
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
                _lastError.value = null
                _userInfoStatus.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "UserInfoViewModel.getUserInfo: ${e.message}", e)
                val error = RequestState.Error(e.resId, e.message)
                _lastError.value = error
                _userInfoStatus.value = error
            }
        }
    }

    /** 未读数独立拉取（页面 active / ON_RESUME 触发；失败仅留痕，角标隐藏） */
    fun loadUnreadCount() {
        viewModelScope.launch {
            try {
                _unreadCount.value = noticeApi.getUnreadCount()
            } catch (e: CancellationException) {
                // 取消不作为网络错误，直接上抛
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "未读数拉取失败（角标隐藏）：${e.message}")
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
