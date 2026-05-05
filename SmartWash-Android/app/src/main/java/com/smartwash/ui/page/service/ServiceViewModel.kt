package com.smartwash.ui.page.service

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.database.dao.LaundryItemDao
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.network.vo.laundry.LaundryItem
import com.smartwash.R
import com.smartwash.repository.LaundryRepository
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
    private val laundryItemDao: LaundryItemDao,
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()

    fun getLaundryItem() {
        viewModelScope.launch {
            // 有缓存时先显示缓存，无缓存时显示 Loading
            val cached = laundryItemDao.getAll()
            if (cached.isNotEmpty()) {
                _laundryItems.value = cached.map { it.toVo() }
            } else {
                _getLaundryItemState.value = RequestState.Loading
            }

            try {
                _laundryItems.value = laundryRepository.getLaundryItems()
                _getLaundryItemState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "ServiceViewModel.getLaundryItem: ${e.message}", e)
                if (cached.isEmpty()) {
                    _getLaundryItemState.value = RequestState.Error(e.resId, e.message)
                }
            }
        }
    }
}
