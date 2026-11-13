package com.smartwash.feature.toolbox.di

import com.smartwash.feature.toolbox.network.AssetTagApi
import com.smartwash.feature.toolbox.network.DeliveryApi
import com.smartwash.feature.toolbox.network.ToolboxApi
import com.smartwash.feature.toolbox.repository.AssetTagRepository
import com.smartwash.feature.toolbox.repository.AssetTagRepositoryImpl
import com.smartwash.feature.toolbox.repository.DeliveryRepository
import com.smartwash.feature.toolbox.repository.DeliveryRepositoryImpl
import com.smartwash.feature.toolbox.repository.ToolboxRepository
import com.smartwash.feature.toolbox.repository.ToolboxRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 工具箱 Hilt 供给：ToolboxApi/DeliveryApi 复用 :common:network 注入的 Retrofit
 * （baseUrl 走主网关 BuildConfig.BASE_URL，鉴权/错误转译拦截器自动生效），
 * Repository 以接口绑定（JVM 单测可替换 Fake）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ToolboxApiModule {

    @Provides
    @Singleton
    fun provideToolboxApi(retrofit: Retrofit): ToolboxApi =
        retrofit.create(ToolboxApi::class.java)

    @Provides
    @Singleton
    fun provideDeliveryApi(retrofit: Retrofit): DeliveryApi =
        retrofit.create(DeliveryApi::class.java)

    @Provides
    @Singleton
    fun provideAssetTagApi(retrofit: Retrofit): AssetTagApi =
        retrofit.create(AssetTagApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolboxRepositoryModule {

    @Binds
    abstract fun bindToolboxRepository(impl: ToolboxRepositoryImpl): ToolboxRepository

    @Binds
    abstract fun bindDeliveryRepository(impl: DeliveryRepositoryImpl): DeliveryRepository

    @Binds
    abstract fun bindAssetTagRepository(impl: AssetTagRepositoryImpl): AssetTagRepository
}
