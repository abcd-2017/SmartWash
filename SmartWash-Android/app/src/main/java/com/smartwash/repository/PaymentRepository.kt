package com.smartwash.repository

import com.smartwash.network.api.PaymentApi
import com.smartwash.network.entity.OrderPayment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val paymentApi: PaymentApi,
) {
    suspend fun payment(orderPayment: OrderPayment) {
        paymentApi.payment(orderPayment)
    }
}
