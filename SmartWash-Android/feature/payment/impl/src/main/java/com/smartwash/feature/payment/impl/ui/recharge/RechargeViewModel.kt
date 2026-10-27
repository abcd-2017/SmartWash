package com.smartwash.feature.payment.impl.ui.recharge

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.payment.impl.PaymentImplConstant
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.impl.network.entity.recharge.UserRecharge
import com.smartwash.feature.payment.impl.repository.RechargeRepository
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
            _balance.value = runCatching { userApi.getUserInfo()?.balance }.getOrNull()
        }
    }

    fun userRecharge(amount: Float, rechargeType: String) {
        _rechargeState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                rechargeRepository.userRecharge(UserRecharge(amount, rechargeType))
                _rechargeState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(PaymentImplConstant.APP_NAME, "RechargeViewModel.userRecharge: ${e.message}", e)
                _rechargeState.value = RequestState.Error(R.string.error_recharge_failed)
            }
        }
    }

    fun setRechargeStateIdle() {
        _rechargeState.value = RequestState.Idle
    }
}
