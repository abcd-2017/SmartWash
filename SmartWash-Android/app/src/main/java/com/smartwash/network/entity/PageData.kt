package com.smartwash.network.entity

import androidx.annotation.Keep

@Keep
data class PageData<T>(
    val records: List<T>,
    val total: Long,
    val size: Int,
    val current: Int
)
