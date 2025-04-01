package com.smartwash.ui.page.laundry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.api.OrderApi
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.vo.laundry.LaundryItem
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LaundryViewModel @Inject constructor(
    private val laundryItemsApi: LaundryItemsApi,
    private val orderApi: OrderApi
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()
    private val _reservationState = MutableStateFlow<RequestState>(RequestState.Idle)
    val reservationState = _reservationState.asStateFlow()
    private val _orderId = MutableStateFlow<Long>(-1)
    val orderId = _orderId.asStateFlow()

    fun getLaundryItem() {
        _getLaundryItemState.value = RequestState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData = laundryItemsApi.getLaundryItems()

                withContext(Dispatchers.Main) {
                    _laundryItems.value = responseData.data ?: emptyList()
                    _getLaundryItemState.value = RequestState.Success
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _getLaundryItemState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    fun resetGetLaundryItemState() {
        _getLaundryItemState.value = RequestState.Idle
    }

    fun reservationLaundry(selectItemId: Long, totalPrice: Float) {
        _reservationState.value = RequestState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val responseData =
                    orderApi.reservationLaundry(ReservationLaundry(selectItemId, totalPrice))
                if (responseData.data != null) {
                    withContext(Dispatchers.Main) {
                        _reservationState.value = RequestState.Success
                        _orderId.value = responseData.data
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _reservationState.value = RequestState.Error("预约失败")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _reservationState.value = RequestState.Error("${e.message}")
                }
            }
        }
    }

    fun resetReservationState() {
        _reservationState.value = RequestState.Idle
    }
}