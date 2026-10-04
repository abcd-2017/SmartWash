package com.smartwash.common.init

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 初始化任务注册表：收集全部 [InitTask] 供 [InitEngine] 执行。
 *
 * 收集机制：各模块在自己的 Hilt Module 中用 `@Provides @IntoSet`（或 `@Binds @IntoSet`）
 * 提供任务，Hilt 多绑定自动聚合成 `Set<InitTask>`——本模块不感知任何实现方。
 */
interface InitTaskRegistry {
    fun getTasks(): List<InitTask>
}

/**
 * 注册表实现：注入 Hilt 多绑定集合。
 *
 * 模块示例（消费方）：
 * ```
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object UpdateInitModule {
 *     @Provides
 *     @IntoSet
 *     fun provideUpdateInitTask(...): InitTask = UpdateInitTask(...)
 * }
 * ```
 */
@Singleton
class InitTaskRegistryImpl @Inject constructor(
    private val tasks: Set<@JvmSuppressWildcards InitTask>,
) : InitTaskRegistry {
    override fun getTasks(): List<InitTask> = tasks.toList()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InitTaskRegistryModule {

    /**
     * 声明 Set 多绑定：任务集允许为空（模块化初期尚无业务任务注册时引擎空跑），
     * 无此声明 Dagger 会在根组件校验时报 MissingBinding。
     */
    @Multibinds
    abstract fun initTasks(): Set<@JvmSuppressWildcards InitTask>

    @Binds
    abstract fun bindRegistry(impl: InitTaskRegistryImpl): InitTaskRegistry
}
