package com.smartwash.ui.page.recharge

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.RechargeApi
import com.smartwash.network.entity.recharge.UserRecharge
import com.smartwash.utils.AppConstant
import com.smartwash.R
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RechargeViewModel @Inject constructor(
    private val rechargeApi: RechargeApi
) : ViewModel() {
    private val _rechargeState = MutableStateFlow<RequestState>(RequestState.Idle)
    val rechargeState = _rechargeState.asStateFlow()

    fun userRecharge(amount: Float, rechargeType: String) {
        _rechargeState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                rechargeApi.userRecharge(UserRecharge(amount, rechargeType))
                _rechargeState.value = RequestState.Success
            } catch (e: Exception) {
                Log.e(AppConstant.APP_NAME, "RechargeViewModel.userRecharge: ${e.message}", e)
                _rechargeState.value = RequestState.Error(R.string.error_recharge_failed)
            }
        }
    }

    fun setRechargeStateIdle() {
        _rechargeState.value = RequestState.Idle
    }
}