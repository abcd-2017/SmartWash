package com.smartwash.feature.dining.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.logic.DiningOrderListItem
import com.smartwash.feature.dining.logic.toOrderListItem
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 订单列表页 ViewModel（域首页）：我的订单（我发起的 + 我参与的）一次性拉取，页面按
 * isCreator 拆分区渲染（后端单接口倒序返回，无分页——家庭场景订单量级小）。
 *
 * 状态划分：[listState] 为列表主流程（骨架/错误/内容）；[orders] 为映射后列表项，
 * 分区派生放页面 remember（组合期纯计算，非副作用）。
 */
@HiltViewModel
class DiningOrderListViewModel @Inject constructor(
    private val diningRepository: DiningRepository,
) : ViewModel() {

    private val _listState = MutableStateFlow<RequestState>(RequestState.Idle)
    val listState = _listState.asStateFlow()

    private val _orders = MutableStateFlow<List<DiningOrderListItem>>(emptyList())
    val orders = _orders.asStateFlow()

    /** 首载/重试/下拉刷新（防重：加载中直接忽略） */
    fun load() {
        if (_listState.value is RequestState.Loading) return
        viewModelScope.launch {
            _listState.value = RequestState.Loading
            try {
                _orders.value = diningRepository.getMyOrders().map { it.toOrderListItem() }
                _listState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderListViewModel.load: ${e.message}", e)
                _listState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningOrderListViewModel"
    }
}
