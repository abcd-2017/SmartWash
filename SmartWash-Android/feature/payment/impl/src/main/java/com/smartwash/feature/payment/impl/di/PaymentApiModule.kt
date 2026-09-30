package com.smartwash.feature.payment.impl.di

import com.smartwash.feature.payment.impl.network.api.PaymentApi
import com.smartwash.feature.payment.impl.network.api.RechargeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

/**
 * 支付域网络供给（T6.2 自 app/di 的 NetworkModule 拆出，模式同 order-impl 的
 * OrderServiceApiModule）：PaymentApi/RechargeApi 实例随支付+充值域迁入本模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentApiModule {

    @Provides
    fun providePaymentApi(retrofit: Retrofit): PaymentApi =
        retrofit.create(PaymentApi::class.java)

    @Provides
    fun provideRechargeApi(retrofit: Retrofit): RechargeApi =
        retrofit.create(RechargeApi::class.java)
}
