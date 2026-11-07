package com.smartwash.feature.memo.di

import com.smartwash.feature.memo.network.MemoApi
import com.smartwash.feature.memo.repository.MemoRepository
import com.smartwash.feature.memo.repository.MemoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 速记手账 Hilt 供给：MemoApi 复用 :common:network 注入的 Retrofit
 * （baseUrl 走主网关 BuildConfig.BASE_URL，鉴权/错误转译拦截器自动生效），
 * Repository 以接口绑定（JVM 单测可替换 Fake）。模式对齐 MoodApiModule/MoodRepositoryModule。
 */
@Module
@InstallIn(SingletonComponent::class)
object MemoApiModule {

    @Provides
    @Singleton
    fun provideMemoApi(retrofit: Retrofit): MemoApi =
        retrofit.create(MemoApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoRepositoryModule {

    @Binds
    abstract fun bindMemoRepository(impl: MemoRepositoryImpl): MemoRepository
}
