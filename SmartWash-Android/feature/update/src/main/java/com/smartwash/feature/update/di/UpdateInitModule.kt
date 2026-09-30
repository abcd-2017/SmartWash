package com.smartwash.feature.update.di

import com.smartwash.core.init.InitTask
import com.smartwash.feature.update.event.UpdateEventBus
import com.smartwash.feature.update.init.UpdateInitTask
import com.smartwash.feature.update.repository.AppUpdateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 启动任务注册（InitTaskRegistry KDoc 示例的首个落地）：
 * UpdateInitTask 经 @IntoSet 多绑定汇入 InitTask 集合，
 * App.onCreate 由 InitEngine 统一调度，无需壳层感知。
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateInitModule {

    @Provides
    @IntoSet
    fun provideUpdateInitTask(
        repository: AppUpdateRepository,
        eventBus: UpdateEventBus,
    ): InitTask = UpdateInitTask(repository, eventBus)
}
