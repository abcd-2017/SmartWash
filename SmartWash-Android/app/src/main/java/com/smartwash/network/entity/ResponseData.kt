package com.smartwash.network.entity

import androidx.annotation.Keep

@Keep
data class ResponseData<out T>(
    val code: Int,
    val message: String,
    val data: T?,
)