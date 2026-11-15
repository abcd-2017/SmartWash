package com.smartwash.di

import com.smartwash.network.api.WorkbenchEntryApi
import com.smartwash.repository.WorkbenchEntryRepository
import com.smartwash.repository.WorkbenchEntryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 工作台入口 Hilt 供给（壳层自有页面，Api/Repository 留 app：
 * 页面在壳层工作台 tab，无域归属，照 app/build.gradle「Retrofit 供 di/ 各业务 API
 * @Provides」的既有口径；复用 :common:network 注入的 Retrofit）。
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkbenchApiModule {

    @Provides
    @Singleton
    fun provideWorkbenchEntryApi(retrofit: Retrofit): WorkbenchEntryApi =
        retrofit.create(WorkbenchEntryApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkbenchRepositoryModule {

    @Binds
    abstract fun bindWorkbenchEntryRepository(impl: WorkbenchEntryRepositoryImpl): WorkbenchEntryRepository
}
