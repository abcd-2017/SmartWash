package com.smartwash.ui.page.service

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.database.dao.LaundryItemDao
import com.smartwash.database.entity.LaundryItemEntity
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
    private val laundryItemDao: LaundryItemDao,
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()

    fun getLaundryItem() {
        viewModelScope.launch {
            // 1. 读取本地缓存
            val cached = laundryItemDao.getAll().map { it.toVo() }
            if (cached.isNotEmpty()) {
                _laundryItems.value = cached
            } else {
                _getLaundryItemState.value = RequestState.Loading
            }

            // 2. 后台请求网络
            try {
                val responseData = laundryItemsApi.getLaundryItems()
                val networkData = responseData.data ?: emptyList()
                _laundryItems.value = networkData
                _getLaundryItemState.value = RequestState.Success
                // 3. 更新缓存
                laundryItemDao.deleteAll()
                laundryItemDao.insertAll(networkData.map { LaundryItemEntity.fromVo(it) })
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "ServiceViewModel.getLaundryItem: ${e.message}", e)
                // 4. 仅在无缓存时显示错误
                if (cached.isEmpty()) {
                    _getLaundryItemState.value = RequestState.Error(e.resId, e.message)
                }
            }
        }
    }
}
