package com.smartwash.feature.order.impl.di

import com.smartwash.feature.order.api.OrderApi
import com.smartwash.feature.order.impl.OrderApiImpl
import com.smartwash.feature.order.impl.network.api.OrderServiceApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

/**
 * 订单域服务契约绑定（T6.1）：
 * - OrderApi → OrderApiImpl：跨模块消费方（app 壳留守页面、T6.2 payment-impl、
 *   T6.3 user-impl 计数切换）经接口访问订单域；
 * - OrderServiceApi 实例随订单域迁入（自 app/di 的 NetworkModule 拆出）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OrderImplModule {

    @Binds
    abstract fun bindOrderApi(impl: OrderApiImpl): OrderApi
}

/** 订单域网络供给（模式同 user-impl 的 UserAccountApiModule） */
@Module
@InstallIn(SingletonComponent::class)
object OrderServiceApiModule {

    @Provides
    fun provideOrderServiceApi(retrofit: Retrofit): OrderServiceApi =
        retrofit.create(OrderServiceApi::class.java)
}
