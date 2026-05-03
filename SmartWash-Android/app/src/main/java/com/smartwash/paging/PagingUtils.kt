package com.smartwash.paging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun <T : Any> ViewModel.pagingFlow(
    trigger: Flow<String>,
    pageSize: Int = 10,
    sourceFactory: (String) -> PagingSource<Int, T>,
) = trigger
    .debounce(300)
    .distinctUntilChanged()
    .flatMapLatest { status ->
        Pager(PagingConfig(pageSize = pageSize)) {
            sourceFactory(status)
        }.flow.cachedIn(viewModelScope)
    }