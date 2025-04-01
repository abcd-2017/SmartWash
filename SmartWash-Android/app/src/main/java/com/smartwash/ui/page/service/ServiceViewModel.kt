package com.smartwash.ui.page.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.LaundryItemsApi
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
class ServiceViewModel @Inject constructor(
    private val laundryItemsApi: LaundryItemsApi,
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()

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
}