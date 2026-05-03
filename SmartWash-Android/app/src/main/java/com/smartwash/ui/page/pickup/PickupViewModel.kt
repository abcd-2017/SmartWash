package com.smartwash.ui.page.pickup

import androidx.lifecycle.ViewModel
import com.smartwash.network.api.OrderApi
import com.smartwash.paging.OrderPagingSource
import com.smartwash.paging.pagingFlow
import com.smartwash.utils.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PickupViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _orderState = MutableStateFlow<String>(OrderStatus.READY_FOR_PICKUP.status)
    val orderState = _orderState.asStateFlow()

    val pagingFlow = pagingFlow(orderState) { status ->
        OrderPagingSource(orderApi, status)
    }
}