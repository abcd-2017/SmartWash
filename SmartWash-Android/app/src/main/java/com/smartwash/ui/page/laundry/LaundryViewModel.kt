package com.smartwash.ui.page.laundry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.database.dao.LaundryItemDao
import com.smartwash.database.entity.LaundryItemEntity
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.laundry.LaundryItem
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.R
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LaundryViewModel @Inject constructor(
    private val laundryItemsApi: LaundryItemsApi,
    private val orderApi: OrderApi,
    private val userApi: UserApi,
    private val laundryItemDao: LaundryItemDao,
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()
    private val _reservationState = MutableStateFlow<RequestState>(RequestState.Idle)
    val reservationState = _reservationState.asStateFlow()
    private val _orderId = MutableStateFlow<Long>(-1)
    val orderId = _orderId.asStateFlow()
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfoVo?>(null)
    val userInfo = _userInfo.asStateFlow()

    fun getLaundryItem() {
        _getLaundryItemState.value = RequestState.Loading
        viewModelScope.launch {
            // 1. 读取本地缓存
            val cached = laundryItemDao.getAll().map { it.toVo() }
            if (cached.isNotEmpty()) {
                _laundryItems.value = cached
                _getLaundryItemState.value = RequestState.Success
            }

            // 2. 后台请求网络
            try {
                val responseData = laundryItemsApi.getLaundryItems()
                val userInfoRes = userApi.getUserInfo()
                _laundryItems.value = responseData.data ?: emptyList()
                _userInfo.value = userInfoRes.data
                _getLaundryItemState.value = RequestState.Success
                // 3. 更新缓存
                laundryItemDao.deleteAll()
                laundryItemDao.insertAll(
                    (responseData.data ?: emptyList()).map { LaundryItemEntity.fromVo(it) }
                )
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "LaundryViewModel.getLaundryItem: ${e.message}", e)
                // 4. 仅在无缓存时显示错误
                if (cached.isEmpty()) {
                    _getLaundryItemState.value = RequestState.Error(e.resId, e.message)
                }
            }
        }
    }

    fun resetGetLaundryItemState() {
        _getLaundryItemState.value = RequestState.Idle
    }

    fun reservationLaundry(selectItemId: Long, totalPrice: Float) {
        _reservationState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val responseData =
                    orderApi.reservationLaundry(ReservationLaundry(selectItemId, totalPrice))
                if (responseData.data != null) {
                    _reservationState.value = RequestState.Success
                    _orderId.value = responseData.data
                } else {
                    _reservationState.value = RequestState.Error(R.string.error_booking_failed)
                }
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "LaundryViewModel.reservationLaundry: ${e.message}", e)
                _reservationState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    fun resetReservationState() {
        _reservationState.value = RequestState.Idle
    }
}
