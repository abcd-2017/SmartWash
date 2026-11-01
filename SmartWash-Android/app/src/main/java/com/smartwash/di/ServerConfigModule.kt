package com.smartwash.di

import com.smartwash.common.init.InitTask
import com.smartwash.init.ServerConfigInitTask
import com.smartwash.network.api.ServerConfigApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 服务端公开配置 Hilt 供给：
 * - [ServerConfigApi] 实例（复用 :common:network 注入的 Retrofit，baseUrl = BuildConfig.BASE_URL）；
 * - [ServerConfigInitTask] 经 @IntoSet 注册进 InitTaskRegistry，App.onCreate 由 InitEngine 调度。
 */
@Module
@InstallIn(SingletonComponent::class)
object ServerConfigModule {

    @Provides
    @Singleton
    fun provideServerConfigApi(retrofit: Retrofit): ServerConfigApi =
        retrofit.create(ServerConfigApi::class.java)

    @Provides
    @IntoSet
    fun provideServerConfigInitTask(api: ServerConfigApi): InitTask =
        ServerConfigInitTask(api)
}
