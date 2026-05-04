package com.smartwash.ui.page.service

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.laundry.LaundryItem
import com.smartwash.R
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val laundryItemsApi: LaundryItemsApi,
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()

    fun getLaundryItem() {
        _getLaundryItemState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val responseData = laundryItemsApi.getLaundryItems()
                _laundryItems.value = responseData.data ?: emptyList()
                _getLaundryItemState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "ServiceViewModel.getLaundryItem: ${e.message}", e)
                _getLaundryItemState.value = RequestState.Error(e.resId)
            }
        }
    }
}