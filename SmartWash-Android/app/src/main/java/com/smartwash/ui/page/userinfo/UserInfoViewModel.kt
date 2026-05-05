package com.smartwash.ui.page.userinfo

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.R
import com.smartwash.network.entity.order.OrderItemCountFrom
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.order.OrderItemCountVo
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.utils.AppConstant
import com.smartwash.repository.OrderRepository
import com.smartwash.repository.UserRepository
import com.smartwash.utils.RequestState
import com.smartwash.utils.ShowOrderStatus
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
    private val orderRepository: OrderRepository,
    private val application: Application,
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()
    private val _orderItemCount = MutableStateFlow<OrderItemCountVo?>(null)
    val orderItemCount = _orderItemCount.asStateFlow()
    private val _bindCampusState = MutableStateFlow<RequestState>(RequestState.Idle)
    val bindCampusState = _bindCampusState.asStateFlow()
    private val _unBindCampusState = MutableStateFlow<RequestState>(RequestState.Idle)
    val unBindCampusState = _unBindCampusState.asStateFlow()
    private val _avatarUploadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val avatarUploadState = _avatarUploadState.asStateFlow()

    fun getUserInfo() {
        viewModelScope.launch {
            try {
                _userInfo.value = userRepository.getUserInfo()
                _orderItemCount.value = orderRepository.getOrderItemCount(
                    OrderItemCountFrom(
                        processingStatus = ShowOrderStatus.WASHING.status,
                        shippedStatus = ShowOrderStatus.PENDING_SHIPMENT.status,
                        pendingPickupStatus = ShowOrderStatus.READY_FOR_PICKUP.status,
                        pendingPaymentStatus = ShowOrderStatus.PENDING_PAYMENT.status
                    )
                )
                _userInfoStatus.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.getUserInfo: ${e.message}", e)
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
                    _bindCampusState.value = RequestState.Success
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.bindCampus: ${e.message}", e)
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
                    _unBindCampusState.value = RequestState.Success
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.unBindCampus: ${e.message}", e)
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
                _avatarUploadState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.uploadAvatar: ${e.message}", e)
                _avatarUploadState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                Log.e(AppConstant.APP_NAME, "UserInfoViewModel.uploadAvatar: ${e.message}", e)
                _avatarUploadState.value = RequestState.Error(R.string.avatar_upload_failed, e.message)
            }
        }
    }

    fun resetAvatarUploadState() {
        _avatarUploadState.value = RequestState.Idle
    }
}
