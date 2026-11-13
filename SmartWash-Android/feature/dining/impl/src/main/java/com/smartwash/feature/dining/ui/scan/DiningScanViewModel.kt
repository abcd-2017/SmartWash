package com.smartwash.feature.dining.ui.scan

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.model.DiningOrderStatus
import com.smartwash.feature.dining.logic.DiningJoinUrlParseResult
import com.smartwash.feature.dining.logic.DiningJoinUrlParser
import com.smartwash.feature.dining.logic.DiningOrderBriefUi
import com.smartwash.feature.dining.logic.toBriefUi
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 加入成功事件（[alreadyJoined]=true 表示 brief 判重发现已在单中，页面提示文案不同） */
data class DiningJoinedOrder(
    val orderId: Long,
    val alreadyJoined: Boolean,
)

/**
 * 扫码加入 ViewModel（08 号票）：识别 → 解析 join URL（纯函数）→ 拉 brief → 确认 → join。
 *
 * 状态划分（对齐项目 RequestState 约定）：
 * - [briefState]：brief 拉取主流程（Idle/Loading/Success/Error；解析失败/已结束/网络失败均进 Error，
 *   页面 Toast 提示后继续扫码）；
 * - [brief]：确认弹窗数据（非空即弹「是否加入点餐环节」；join 进行中置空防重复提交）；
 * - [joinState]：加入动作态（Loading 防重；失败回 Error 由页面提示，brief 保留可重试）；
 * - [joined]：一次性导航事件（页面消费后 [consumeJoined]）。
 *
 * 判重与提前提示：brief.joined=true 直接发 [joined]（不弹确认）；brief.status=FINISHED 直接 Error
 * 「该点单已结束」，均避免无效弹窗。
 */
@HiltViewModel
class DiningScanViewModel @Inject constructor(
    private val diningRepository: DiningRepository,
) : ViewModel() {

    private val _briefState = MutableStateFlow<RequestState>(RequestState.Idle)
    val briefState = _briefState.asStateFlow()

    private val _brief = MutableStateFlow<DiningOrderBriefUi?>(null)
    val brief = _brief.asStateFlow()

    private val _joinState = MutableStateFlow<RequestState>(RequestState.Idle)
    val joinState = _joinState.asStateFlow()

    private val _joined = MutableStateFlow<DiningJoinedOrder?>(null)
    val joined = _joined.asStateFlow()

    /** 待加入凭证（brief 接口不回传 token，join 用解析阶段捕获的值） */
    private var pendingJoinToken: String = ""

    /** 扫码去抖：同一原始内容在窗口内只处理一次（相机连续帧会重复回调） */
    private var lastScannedRaw: String? = null
    private var lastScannedAt: Long = 0L

    /** 相机识别回调（页面分析器触发；去抖 + 状态守卫） */
    fun onCodeScanned(raw: String) {
        if (_briefState.value is RequestState.Loading) return
        if (_joinState.value is RequestState.Loading) return
        if (_brief.value != null) return

        val now = SystemClock.elapsedRealtime()
        if (raw == lastScannedRaw && now - lastScannedAt < SCAN_DEBOUNCE_MS) return
        lastScannedRaw = raw
        lastScannedAt = now

        when (val parsed = DiningJoinUrlParser.parse(raw)) {
            is DiningJoinUrlParseResult.Join -> loadBrief(parsed.orderId, parsed.token)
            DiningJoinUrlParseResult.NotDining ->
                _briefState.value = RequestState.Error(R.string.dining_scan_not_our_code)
            DiningJoinUrlParseResult.Malformed ->
                _briefState.value = RequestState.Error(R.string.dining_scan_invalid_link)
        }
    }

    /** 拉取 brief（token 无效/订单不存在由后端业务失败转译上抛） */
    private fun loadBrief(orderId: Long, token: String) {
        if (orderId <= 0L) {
            _briefState.value = RequestState.Error(R.string.dining_scan_invalid_link)
            return
        }
        pendingJoinToken = token
        _briefState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val brief = diningRepository.getOrderBrief(token).toBriefUi()
                if (brief.status == DiningOrderStatus.FINISHED) {
                    _briefState.value = RequestState.Error(R.string.dining_scan_order_finished)
                    return@launch
                }
                if (brief.joined) {
                    _brief.value = null
                    _briefState.value = RequestState.Success
                    _joined.value = DiningJoinedOrder(brief.orderId, alreadyJoined = true)
                    return@launch
                }
                _brief.value = brief
                _briefState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningScanViewModel.loadBrief: ${e.message}", e)
                _briefState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    /** 确认加入（token/状态/重复加入失败由后端业务失败转译上抛；brief 保留便于重试） */
    fun join() {
        val current = _brief.value ?: return
        if (current.orderId <= 0L || pendingJoinToken.isBlank()) {
            _joinState.value = RequestState.Error(R.string.dining_scan_invalid_link)
            return
        }
        if (_joinState.value is RequestState.Loading) return
        _joinState.value = RequestState.Loading
        // 防重复提交：join 期间不弹确认（若失败由页面恢复 brief 展示）
        _brief.value = null
        viewModelScope.launch {
            try {
                diningRepository.joinOrder(current.orderId, pendingJoinToken)
                _joinState.value = RequestState.Success
                _joined.value = DiningJoinedOrder(current.orderId, alreadyJoined = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningScanViewModel.join: ${e.message}", e)
                _joinState.value = RequestState.Error(e.resId, e.message)
                _brief.value = current
            }
        }
    }

    /** 关闭确认弹窗（用户取消；相机继续识别，去抖窗口后同一码可再次触发） */
    fun dismissBrief() {
        _brief.value = null
    }

    /** 消费一次性加入事件（页面导航完成后调用） */
    fun consumeJoined() {
        _joined.value = null
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningScanViewModel"

        /** 同一二维码去抖窗口（毫秒） */
        const val SCAN_DEBOUNCE_MS = 1500L
    }
}
