package com.smartwash.ui.page.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.smartwash.network.api.OrderApi
import com.smartwash.paging.MyPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _orderState = MutableStateFlow<String>("")
    val orderState = _orderState.asStateFlow()

    fun updateOrderStatus(status: String) {
        _orderState.value = status
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingFlow = orderState
        .debounce(300)//防抖
        .distinctUntilChanged()
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 10)) {
                MyPagingSource(orderApi, it)
            }.flow.cachedIn(viewModelScope)
        }
}