package com.smartwash.feature.laundry.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.laundry.network.vo.LaundryItem
import com.smartwash.feature.laundry.repository.LaundryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
) : ViewModel() {
    private val _getLaundryItemState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getLaundryItemState = _getLaundryItemState.asStateFlow()
    private val _laundryItems = MutableStateFlow<List<LaundryItem>>(emptyList())
    val laundryItems = _laundryItems.asStateFlow()

    fun getLaundryItem() {
        viewModelScope.launch {
            // 有缓存时先显示缓存，无缓存时显示 Loading（缓存读取统一走 Repository，VM 不直接持有 DAO）
            val cached = laundryRepository.getCachedLaundryItems()
            if (cached.isNotEmpty()) {
                _laundryItems.value = cached
            } else {
                _getLaundryItemState.value = RequestState.Loading
            }

            try {
                _laundryItems.value = laundryRepository.getLaundryItems()
                _getLaundryItemState.value = RequestState.Success
                // 主页底部 Tab 页，切换即触发（偏高频路径）：加载完成只打 debug，不打 info
                AppLogger.debug(TAG, "getLaundryItem: 洗衣项目加载完成, size=${_laundryItems.value.size}")
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "ServiceViewModel.getLaundryItem: ${e.message}", e)
                if (cached.isEmpty()) {
                    _getLaundryItemState.value = RequestState.Error(e.resId, e.message)
                }
            }
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "ServiceViewModel"
    }
}
