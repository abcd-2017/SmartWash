package com.smartwash.network.entity

import androidx.annotation.Keep

@Keep
data class ApiResult<out T>(
    val code: Int,
    val message: String,
    val data: T?,
)
