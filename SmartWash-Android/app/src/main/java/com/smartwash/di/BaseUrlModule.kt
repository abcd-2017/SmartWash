package com.smartwash.di

import com.smartwash.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/** 环境地址供给（壳层提供，common:network 消费）。 */
@Module
@InstallIn(SingletonComponent::class)
object BaseUrlModule {
    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.BASE_URL
}
