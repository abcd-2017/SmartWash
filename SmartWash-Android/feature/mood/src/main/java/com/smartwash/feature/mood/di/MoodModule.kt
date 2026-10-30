package com.smartwash.feature.mood.di

import com.smartwash.feature.mood.network.MoodApi
import com.smartwash.feature.mood.repository.MoodRepository
import com.smartwash.feature.mood.repository.MoodRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 情绪气象站 Hilt 供给：MoodApi 复用 :common:network 注入的 Retrofit
 * （baseUrl 走主网关 BuildConfig.BASE_URL，鉴权/错误转译拦截器自动生效），
 * Repository 以接口绑定（JVM 单测可替换 Fake）。
 */
@Module
@InstallIn(SingletonComponent::class)
object MoodApiModule {

    @Provides
    @Singleton
    fun provideMoodApi(retrofit: Retrofit): MoodApi =
        retrofit.create(MoodApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MoodRepositoryModule {

    @Binds
    abstract fun bindMoodRepository(impl: MoodRepositoryImpl): MoodRepository
}
