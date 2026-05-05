package com.smartwash.utils

import android.content.Context
import androidx.annotation.StringRes

sealed class RequestState {
    data object Idle : RequestState()
    data object Loading : RequestState()
    data object Success : RequestState()
    data class Error(
        @StringRes val messageResId: Int,
        val message: String? = null
    ) : RequestState() {
        fun getMessage(context: Context): String {
            return message?.takeIf { it.isNotBlank() } ?: context.getString(messageResId)
        }
    }
}
