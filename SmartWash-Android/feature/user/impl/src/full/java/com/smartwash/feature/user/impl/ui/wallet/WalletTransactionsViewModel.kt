package com.smartwash.feature.user.impl.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.pagingFlow
import com.smartwash.feature.user.impl.network.api.TransactionApi
import com.smartwash.feature.user.impl.paging.TransactionPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class WalletTransactionsViewModel @Inject constructor(
    private val transactionApi: TransactionApi,
) : ViewModel() {

    private val _trigger = MutableStateFlow("")

    /** 钱包流水分页流（Paging 3，pagingFlow 统一封装 debounce + flatMapLatest + cachedIn） */
    val pagingFlow = pagingFlow(_trigger, pageSize = TransactionPagingSource.PAGE_SIZE) {
        TransactionPagingSource(transactionApi)
    }
}
