package com.smartwash.ui.page.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.model.UserInfo
import com.smartwash.R
import com.smartwash.common.utils.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IndexViewModel @Inject constructor(
    private val userApi: UserApi,
    private val orderApi: OrderApi,
) : ViewModel() {
    private val _userInfoStatus = MutableStateFlow<RequestState>(RequestState.Idle)
    val userInfoStatus = _userInfoStatus.asStateFlow()
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo = _userInfo.asStateFlow()
    private val _orderList = MutableStateFlow<List<OrderBrief>>(emptyList())
    val orderList = _orderList.asStateFlow()

    /**
     * 最近一次首屏加载的失败载荷（回归 2026-11-06 A3）：null=无失败，非 null=携带具体文案。
     * userInfoStatus 的 Error 会被消费后回 Idle，页面无法据此把「失败态」从骨架中摘出来——
     * 断网冷启动时骨架条件（Idle/Loading 且无数据）会让骨架 >30s 死锁。本状态独立承载失败语义：
     * 无数据时驱动错误卡（替代死锁骨架），有数据时驱动离线提示条，直到下一次成功清除。
     */
    private val _lastError = MutableStateFlow<RequestState.Error?>(null)
    val lastError = _lastError.asStateFlow()

    /** 静默强刷进行中标记：驱动下拉刷新指示器（Loading 语义已被骨架屏占用，不复用 userInfoStatus） */
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    fun getInfoData() {
        viewModelScope.launch {
            _userInfoStatus.value = RequestState.Loading
            try {
                // UserApi.getUserInfo：登录后首次走网络、之后命中内存缓存（T5.3 起替代
                // 每页各自经 UserRepository 网络拉取）；未登录/失败返回 null，页面按空数据兜底
                _userInfo.value = userApi.getUserInfo()
                _orderList.value = orderApi.getWashingOrder()
                _lastError.value = null
                _userInfoStatus.value = RequestState.Success
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "IndexViewModel.getInfoData: ${e.message}", e)
                val error = RequestState.Error(e.resId, e.message)
                _lastError.value = error
                _userInfoStatus.value = error
            }
        }
    }

    /**
     * 静默强刷（P-603/P-701）：ON_RESUME 回首页与下拉刷新共用入口。
     *
     * 与 [getInfoData] 的两点差异：
     * 1. 用户信息走 [UserApi.refreshUserInfo] 强制走网络——余额/统计在服务端被支付、
     *    充值、订单流转等操作改变后，仅重读 UserApiImpl 内存缓存拿不到新值；
     * 2. 不置 userInfoStatus（不清空现有内容、不触发骨架/Crossfade 闪变），刷新进度
     *    由 [refreshing] 独立承载（下拉指示器）。失败静默降级保留现有数据。
     *
     * A3：尚无任何数据（断网冷启动首刷失败/未加载）时静默强刷救不了页面——失败被吞、
     * 错误态永远等不到数据。此时转完整加载 [getInfoData]（带状态流转，失败可再出错误卡），
     * 失败状态下拉刷新/ON_RESUME 才能真正重新发起请求。
     */
    fun refreshData() {
        if (_userInfo.value == null) {
            getInfoData()
            return
        }
        // 防重入：下拉刷新与 ON_RESUME 短窗口内可能并发触发，同一时间只允许一轮在跑
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                // refreshUserInfo 失败返回 null（实现方已 catch），保留旧值不覆盖
                _userInfo.value = userApi.refreshUserInfo() ?: _userInfo.value
                _orderList.value = orderApi.getWashingOrder()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.warn(TAG, "refreshData: 静默强刷失败，保留现有数据", e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "IndexViewModel"
    }
}
