package com.smartwash.di

import android.content.Context
import androidx.work.WorkManager
import com.smartwash.network.api.AppUpdateApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 应用更新模块依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideAppUpdateApi(retrofit: Retrofit): AppUpdateApi =
        retrofit.create(AppUpdateApi::class.java)

    /** WorkManager 实例（APK 下载任务入队/监听共用） */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
