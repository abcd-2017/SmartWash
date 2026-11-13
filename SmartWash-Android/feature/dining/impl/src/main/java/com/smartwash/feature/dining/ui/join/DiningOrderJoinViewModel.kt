package com.smartwash.feature.dining.ui.join

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningJoinEntry
import com.smartwash.feature.dining.logic.DiningJoinEntryDecision
import com.smartwash.feature.dining.logic.toBriefUi
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 点单加入入口 ViewModel（12 号票）：站内信 dining 深链落地页。
 *
 * 流程：拉 brief（凭深链携带的 join_token）→ [DiningJoinEntry] 纯函数决策：
 * - 已是成员 → 发 [navigateToDetail] 直达点单页；
 * - 非成员且进行中 → 展示 brief（标题/发起者/菜品数）+ 「加入」按钮，确认后 join 再发导航；
 * - 非成员且已结束 → 提示不可加入；
 * - 失败 → [briefState]/[joinState] 错误态由页面提示。
 *
 * [navigateToDetail] 为一次性事件（页面消费后 [consumeNavigateToDetail]），
 * 避免重组重放导致重复导航。
 */
@HiltViewModel
class DiningOrderJoinViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diningRepository: DiningRepository,
) : ViewModel() {

    /** 路由参数：订单 ID（0=非法，不发起请求） */
    val orderId: Long = savedStateHandle.get<Long>(ARG_ORDER_ID) ?: 0L

    /** 路由参数：加入凭证（深链 query t，缺失为空串由后端判无效） */
    private val token: String = savedStateHandle.get<String>(ARG_TOKEN).orEmpty()

    private val _briefState = MutableStateFlow<RequestState>(RequestState.Idle)
    val briefState = _briefState.asStateFlow()

    private val _brief = MutableStateFlow<com.smartwash.feature.dining.logic.DiningOrderBriefUi?>(null)
    val brief = _brief.asStateFlow()

    private val _joinState = MutableStateFlow<RequestState>(RequestState.Idle)
    val joinState = _joinState.asStateFlow()

    /** 一次性导航事件：携带目标订单 ID（0=不导航，页面按错误兜底） */
    private val _navigateToDetail = MutableStateFlow<Long?>(null)
    val navigateToDetail = _navigateToDetail.asStateFlow()

    /** 首载/重试（防重：加载中忽略） */
    fun load() {
        if (_briefState.value is RequestState.Loading) return
        if (orderId <= 0L) {
            _briefState.value = RequestState.Error(R.string.dining_order_join_invalid_token)
            return
        }
        viewModelScope.launch {
            _briefState.value = RequestState.Loading
            try {
                val ui = diningRepository.getOrderBrief(token).toBriefUi()
                _brief.value = ui
                _briefState.value = RequestState.Success
                if (DiningJoinEntry.decide(ui) == DiningJoinEntryDecision.ENTER_DETAIL && ui.orderId > 0L) {
                    _navigateToDetail.value = ui.orderId
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderJoinViewModel.load: ${e.message}", e)
                _briefState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 确认加入（token/状态/重复加入失败由后端业务失败转译上抛，页面提示后可重试） */
    fun join() {
        val current = _brief.value ?: return
        if (current.orderId <= 0L || token.isBlank()) {
            _joinState.value = RequestState.Error(R.string.dining_order_join_invalid_token)
            return
        }
        if (_joinState.value is RequestState.Loading) return
        viewModelScope.launch {
            _joinState.value = RequestState.Loading
            try {
                diningRepository.joinOrder(current.orderId, token)
                _joinState.value = RequestState.Success
                _navigateToDetail.value = current.orderId
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderJoinViewModel.join: ${e.message}", e)
                _joinState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 消费一次性导航事件（页面导航完成后调用） */
    fun consumeNavigateToDetail() {
        _navigateToDetail.value = null
    }

    /** 消费加入失败态（页面 Toast 后调用，避免重放） */
    fun consumeJoinState() {
        _joinState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningOrderJoinViewModel"

        /** 类型安全路由参数键（DiningRoute.OrderJoin 属性名即键名） */
        const val ARG_ORDER_ID = "orderId"
        const val ARG_TOKEN = "token"
    }
}
