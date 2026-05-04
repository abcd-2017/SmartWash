package com.smartwash.utils

import androidx.annotation.StringRes

sealed class RequestState {
    data object Idle : RequestState()
    data object Loading : RequestState()
    data object Success : RequestState()
    data class Error(@StringRes val messageResId: Int) : RequestState()
}
