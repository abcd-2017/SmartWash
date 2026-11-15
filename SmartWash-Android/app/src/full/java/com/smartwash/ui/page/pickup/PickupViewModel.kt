package com.smartwash.ui.page.pickup

import androidx.lifecycle.ViewModel
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.common.utils.pagingFlow
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
        orderApi.orderPagingSource(status)
    }
}
