package com.smartwash.feature.payment.impl.ui.recharge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.impl.network.entity.recharge.UserRecharge
import com.smartwash.feature.payment.impl.repository.RechargeRepository
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.user.api.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RechargeViewModel @Inject constructor(
    private val rechargeRepository: RechargeRepository,
    private val userApi: UserApi,
) : ViewModel() {
    private val _rechargeState = MutableStateFlow<RequestState>(RequestState.Idle)
    val rechargeState = _rechargeState.asStateFlow()

    // 账户余额（充值页余额区展示，画板实测 display 大数字档）。
    // 经 UserApi 取——getUserInfo 自带内存缓存，登录态下无额外网络请求；
    // 未登录/获取失败保持 null，页面显示占位而非编造 0.00。
    private val _balance = MutableStateFlow<Float?>(null)
    val balance = _balance.asStateFlow()

    init {
        viewModelScope.launch {
            // 降级留痕：余额获取失败不阻塞页面（余额区显示占位），级别取 warn 对齐
            // SessionInitTask 等静默预热失败的既有先例
            _balance.value = runCatching { userApi.getUserInfo()?.balance }
                .onFailure { AppLogger.warn(TAG, "init: 获取账户余额失败，余额区降级为占位显示", it) }
                .getOrNull()
        }
    }

    fun userRecharge(amount: Float, rechargeType: String) {
        _rechargeState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                // 发起充值以 debug 留业务键：后端业务拒绝（金额超限/校验不过等）经
                // ResponseInterceptor 统一 warn 留痕但不带金额，凭本行在 debug 构建对齐
                AppLogger.debug(TAG, "userRecharge: 发起充值 amount=$amount rechargeType=$rechargeType")
                rechargeRepository.userRecharge(UserRecharge(amount, rechargeType))
                // 低频关键动作成功：充值到账（资金链路核心留痕）
                AppLogger.info(TAG, "userRecharge: 充值成功 amount=$amount rechargeType=$rechargeType")
                // 充值成功即强刷用户信息（2026-11-01 缺陷 8d）：后端充值接口不回传新余额，
                // 且 UserApiImpl 内存缓存需显式刷新——否则本页与首页/用户中心的余额
                // 全部停留在旧值，直到重新登录。刷新失败不阻塞成功反馈（下次进页面重试）
                runCatching { userApi.refreshUserInfo() }
                    .onSuccess { refreshed -> refreshed?.let { _balance.value = it.balance } }
                    // 降级留痕：刷新失败不影响成功反馈，但余额可能停留旧值（下次进页面重试）
                    .onFailure {
                        AppLogger.warn(TAG, "userRecharge: 充值后余额刷新失败，余额可能停留旧值（下次进页重试）", it)
                    }
                _rechargeState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "RechargeViewModel.userRecharge: ${e.message}", e)
                _rechargeState.value = RequestState.Error(R.string.error_recharge_failed)
            }
        }
    }

    fun setRechargeStateIdle() {
        _rechargeState.value = RequestState.Idle
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "RechargeViewModel"
    }
}
