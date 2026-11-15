package com.smartwash.feature.update.di

import android.content.Context
import androidx.work.WorkManager
import com.smartwash.feature.update.api.network.AppUpdateApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 热更新内核依赖注入（原 :feature:update:impl 的 UpdateModule 拆分）：
 * - [AppUpdateApi]：版本检查 / 预签名下载地址（AppUpdateRepository 消费）
 * - [WorkManager]：APK 下载任务入队 / 进度监听（壳层与 Worker 共用）
 *
 * 两变体共用，故置本模块（core）而非 impl。
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateCoreModule {

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
