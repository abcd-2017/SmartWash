package com.smartwash.network.entity.order

import androidx.annotation.Keep

@Keep
data class OrderListFrom(val status: String, val page: Int?, val size: Int? = 10)
