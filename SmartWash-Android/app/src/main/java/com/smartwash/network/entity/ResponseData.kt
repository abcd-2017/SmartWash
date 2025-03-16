package com.smartwash.network.entity

data class ResponseData<out T>(
    val code: Int,
    val message: String,
    val data: T?
)