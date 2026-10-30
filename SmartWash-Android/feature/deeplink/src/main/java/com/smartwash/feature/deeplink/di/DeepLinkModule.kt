package com.smartwash.feature.deeplink.di

import com.smartwash.feature.deeplink.DeepLinkRegistry
import com.smartwash.feature.deeplink.DefaultDeepLinkRegistry
import com.smartwash.feature.deeplink.network.DeepLinkApi
import com.smartwash.feature.deeplink.repository.DeepLinkRepository
import com.smartwash.feature.deeplink.repository.DeepLinkRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 深链 Hilt 供给：
 * - DeepLinkApi 复用 :common:network 注入的 Retrofit（baseUrl 走主网关 BuildConfig.BASE_URL，
 *   鉴权/错误转译拦截器自动生效）；
 * - Registry / Repository 以接口供给（JVM 单测可替换 Fake）；
 * - DeepLinkHandler 为 @Singleton @Inject 构造，无需模块登记。
 */
@Module
@InstallIn(SingletonComponent::class)
object DeepLinkApiModule {

    @Provides
    @Singleton
    fun provideDeepLinkApi(retrofit: Retrofit): DeepLinkApi =
        retrofit.create(DeepLinkApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DeepLinkRegistryModule {

    @Provides
    @Singleton
    fun provideDeepLinkRegistry(): DeepLinkRegistry = DefaultDeepLinkRegistry()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeepLinkRepositoryModule {

    @Binds
    abstract fun bindDeepLinkRepository(impl: DeepLinkRepositoryImpl): DeepLinkRepository
}
