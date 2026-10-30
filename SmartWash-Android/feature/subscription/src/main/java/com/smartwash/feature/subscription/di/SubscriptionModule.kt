package com.smartwash.feature.subscription.di

import com.smartwash.feature.subscription.network.SubscriptionApi
import com.smartwash.feature.subscription.repository.SubscriptionRepository
import com.smartwash.feature.subscription.repository.SubscriptionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 订阅 Hilt 供给：SubscriptionApi 复用 :common:network 注入的 Retrofit
 * （baseUrl 走主网关 BuildConfig.BASE_URL，鉴权/错误转译拦截器自动生效），
 * Repository 以接口绑定（对齐 ToolboxModule 先例）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SubscriptionApiModule {

    @Provides
    @Singleton
    fun provideSubscriptionApi(retrofit: Retrofit): SubscriptionApi =
        retrofit.create(SubscriptionApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SubscriptionRepositoryModule {

    @Binds
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository
}
