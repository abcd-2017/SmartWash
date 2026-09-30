package com.smartwash.feature.payment.impl.ui.recharge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.payment.impl.paging.RechargeRecordPagingSource
import com.smartwash.feature.payment.impl.repository.RechargeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class RechargeRecordViewModel @Inject constructor(
    private val rechargeRepository: RechargeRepository,
) : ViewModel() {

    private val _trigger = MutableStateFlow("")

    val pagingFlow = pagingFlow(_trigger) {
        RechargeRecordPagingSource(rechargeRepository.rechargeApi)
    }
}
