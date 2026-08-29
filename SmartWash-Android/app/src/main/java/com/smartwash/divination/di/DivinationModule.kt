package com.smartwash.divination.di

import com.smartwash.divination.data.DivRecordDao
import com.smartwash.divination.network.DivinationApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 观象台 Hilt 供给：Room DAO（复用既有 AppDatabase）与解读 API（复用既有 Retrofit，
 * 鉴权/错误转译拦截器自动生效）。不改动既有 RetrofitClient。
 */
@Module
@InstallIn(SingletonComponent::class)
object DivinationModule {

    @Provides
    fun provideDivRecordDao(database: com.smartwash.database.AppDatabase): DivRecordDao =
        database.divRecordDao()

    @Provides
    @Singleton
    fun provideDivinationApi(retrofit: Retrofit): DivinationApi = retrofit.create(DivinationApi::class.java)
}
