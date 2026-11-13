package com.smartwash.feature.dining.ui.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningShoppingListResult
import com.smartwash.feature.dining.logic.parseDiningShoppingList
import com.smartwash.feature.dining.logic.resolveMyUserId
import com.smartwash.feature.dining.logic.toShoppingListResult
import com.smartwash.feature.dining.repository.DiningRepository
import com.smartwash.feature.user.api.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 购买清单结果页 ViewModel（11 号票）：详情接口拉取 shopping_list 快照与参与者，
 * 解析为双视图模型（[parseDiningShoppingList] + [toShoppingListResult] 纯函数）。
 *
 * 入口有两处：结束点单成功后进入；FINISHED 只读态点单页经「查看购买清单」进入。
 * 快照缺失/畸形一律降级为空清单（结果页展示空态文案，不崩溃）。
 */
@HiltViewModel
class DiningOrderShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diningRepository: DiningRepository,
    private val userApi: UserApi,
) : ViewModel() {

    /** 路由参数：订单 ID（0=非法，不发起请求） */
    val orderId: Long = savedStateHandle.get<Long>(ARG_ORDER_ID) ?: 0L

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _result = MutableStateFlow<DiningShoppingListResult?>(null)
    val result = _result.asStateFlow()

    /** 首载/重试（防重：加载中忽略） */
    fun load() {
        if (_loadState.value is RequestState.Loading) return
        if (orderId <= 0L) {
            _loadState.value = RequestState.Error(R.string.dining_shopping_list_load_failed)
            return
        }
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                val detail = diningRepository.getOrderDetail(orderId)
                val phone = userApi.getUserInfo()?.phoneNumber
                val myUserId = resolveMyUserId(detail.safeMembers, phone)
                _result.value = parseDiningShoppingList(detail.safeShoppingList)
                    ?.toShoppingListResult(detail.safeMembers, myUserId)
                    ?: DiningShoppingListResult(emptyList(), emptyList())
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderShoppingListViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningOrderShoppingListViewModel"

        /** 类型安全路由参数键（DiningRoute.ShoppingList 属性名即键名） */
        const val ARG_ORDER_ID = "orderId"
    }
}
