package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.OrderPayment
import com.smartwash.network.entity.ApiResult
import retrofit2.http.Body
import retrofit2.http.POST

interface PaymentApi {
    @RequireAuthorization
    @POST("/web/auth/payments/payment")
    suspend fun payment(
        @Body orderPayment: OrderPayment
    ): ApiResult<String>
}