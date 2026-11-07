package com.smartwash.feature.readlater.di

import com.smartwash.feature.readlater.network.ReadLaterApi
import com.smartwash.feature.readlater.repository.ReadLaterRepository
import com.smartwash.feature.readlater.repository.ReadLaterRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 稍后读 Hilt 供给：ReadLaterApi 复用 :common:network 注入的 Retrofit
 * （baseUrl 走主网关 BuildConfig.BASE_URL，鉴权/错误转译拦截器自动生效），
 * Repository 以接口绑定（对齐 mood/toolbox 先例）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReadLaterApiModule {

    @Provides
    @Singleton
    fun provideReadLaterApi(retrofit: Retrofit): ReadLaterApi =
        retrofit.create(ReadLaterApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReadLaterRepositoryModule {

    @Binds
    abstract fun bindReadLaterRepository(impl: ReadLaterRepositoryImpl): ReadLaterRepository
}
