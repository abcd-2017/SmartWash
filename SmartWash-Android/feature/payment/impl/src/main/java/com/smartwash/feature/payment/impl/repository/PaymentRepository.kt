package com.smartwash.feature.payment.impl.repository

import com.smartwash.feature.payment.impl.network.api.PaymentApi
import com.smartwash.feature.payment.impl.network.entity.OrderPayment
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
