package com.smartwash.feature.payment.impl.ui.recharge

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.feature.payment.impl.PaymentImplConstant
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.impl.network.entity.recharge.UserRecharge
import com.smartwash.feature.payment.impl.repository.RechargeRepository
import com.smartwash.common.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RechargeViewModel @Inject constructor(
    private val rechargeRepository: RechargeRepository,
) : ViewModel() {
    private val _rechargeState = MutableStateFlow<RequestState>(RequestState.Idle)
    val rechargeState = _rechargeState.asStateFlow()

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
