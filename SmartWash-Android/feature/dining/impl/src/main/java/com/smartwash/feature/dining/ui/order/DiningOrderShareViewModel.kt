package com.smartwash.feature.dining.ui.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import javax.inject.Named
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.dining.logic.DiningJoinUrlBuilder
import com.smartwash.feature.dining.logic.DiningOrderShareDetail
import com.smartwash.feature.dining.logic.toOrderShareDetail
import com.smartwash.feature.dining.repository.DiningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 订单分享视图 ViewModel（07 号票发起链路终点）：订单基本信息（标题/状态/菜品数/参与人数，
 * 经详情接口全量拉取）+ 加入二维码内容构造（DiningJoinUrlBuilder，BASE_URL origin + join_token）。
 *
 * 路由参数（SavedStateHandle，类型安全路由属性名同源）：orderId 必填；title 为入口顺带下发
 * 的首帧文案；joinToken 为可选入口值（创建响应 / 点单页详情）。入口未携带时经详情接口自取：
 * 发起者全量详情响应回传 token（09 号票修补条），参与者仍降级提示「仅发起者可生成二维码」。
 *
 * BASE_URL 经 @Named("baseUrl") 注入（Hilt 全图共享，对齐 SseClient/TokenRefresherImpl 先例，
 * 模块无需依赖 app 壳），二维码 host 与 App 实际访问后端地址同源，无硬编码地址。
 */
@HiltViewModel
class DiningOrderShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diningRepository: DiningRepository,
    @Named("baseUrl") private val baseUrl: String,
) : ViewModel() {

    /** 路由参数：订单 ID（0=非法，详情拉取守卫不发起） */
    val orderId: Long = savedStateHandle.get<Long>(ARG_ORDER_ID) ?: 0L

    /** 路由参数：入口顺带下发的标题（首帧展示；详情成功后以详情为准） */
    val routeTitle: String = savedStateHandle.get<String>(ARG_TITLE).orEmpty()

    /** 路由参数：加入凭证（创建响应 / 点单页详情；空白=入口未携带，经详情自取） */
    val joinToken: String = savedStateHandle.get<String>(ARG_JOIN_TOKEN).orEmpty()

    private val _detailState = MutableStateFlow<RequestState>(RequestState.Idle)
    val detailState = _detailState.asStateFlow()

    private val _detail = MutableStateFlow<DiningOrderShareDetail?>(null)
    val detail = _detail.asStateFlow()

    /**
     * 生效加入凭证：入口携带优先；入口未携带时取详情全量响应回传的 token（仅发起者下发）。
     * 读 `_detail` 使详情到达触发页面重组（页面已收集 detail，重组时重算）。
     */
    val effectiveJoinToken: String
        get() = joinToken.ifBlank { _detail.value?.joinToken.orEmpty() }

    /** 二维码内容 URL（joinUrlBuilder 构造失败/null=token 缺失或 baseUrl 畸形，页面降级提示） */
    val joinUrl: String?
        get() = DiningJoinUrlBuilder.buildJoinUrl(baseUrl, orderId, effectiveJoinToken)

    /** 详情拉取（首次全量：knownVersion 不传；带 version 轮询为点单页后续票消费） */
    fun load() {
        if (_detailState.value is RequestState.Loading) return
        if (orderId <= 0L) {
            _detailState.value = RequestState.Error(com.smartwash.common.network.R.string.error_network_fail)
            return
        }
        viewModelScope.launch {
            _detailState.value = RequestState.Loading
            try {
                _detail.value = diningRepository.getOrderDetail(orderId).toOrderShareDetail()
                _detailState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "DiningOrderShareViewModel.load: ${e.message}", e)
                _detailState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "DiningOrderShareViewModel"

        /** 类型安全路由参数键（DiningRoute.OrderShare 属性名即键名） */
        const val ARG_ORDER_ID = "orderId"
        const val ARG_TITLE = "title"
        const val ARG_JOIN_TOKEN = "joinToken"
    }
}
