package com.smartwash.ui.page.recharge

import androidx.lifecycle.ViewModel
import com.smartwash.paging.RechargeRecordPagingSource
import com.smartwash.paging.pagingFlow
import com.smartwash.repository.RechargeRepository
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
