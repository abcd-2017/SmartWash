package com.smartwash.feature.payment.impl.network.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.payment.impl.network.entity.OrderPayment
import retrofit2.http.Body
import retrofit2.http.POST

interface PaymentApi {
    @RequireAuthorization
    @POST("/web/auth/payments/payment")
    suspend fun payment(
        @Body orderPayment: OrderPayment
    ): ApiResult<String>
}
