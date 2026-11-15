package com.smartwash.common.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * 导航图贡献者契约 —— 对齐 common:init 的 InitTask / common:utils 的 DeepLinkContributor 模式：
 * 契约放公共层（零 feature 依赖），**各域 impl 模块在自己的 Hilt Module 中以
 * `@Provides @IntoSet` 贡献本域 [NavGraphBuilder] 注册逻辑**，壳层 MainActivity 注入
 * `Set<NavGraphContributor>` 后统一 `register`，不再硬编码静态 import + 逐个调用。
 *
 * 注册方法**必须是非 composable**：Navigation 的 `NavHost(builder: NavGraphBuilder.() -> Unit)`
 * 内部经 `remember` 计算调用 builder，该 lambda 不是组合上下文（`composable {}` 注册本身也不
 * 是组合操作），因此图内无法读取 CompositionLocal。需要 reduced-motion 等组合期值的图
 * （userGraph/paymentGraph）改读 [NavGraphAmbient] 快照，该快照由壳层在组合期写入。
 *
 * 示例（某域 impl 模块 di/GraphModule.kt）：
 * ```
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object XxxGraphModule {
 *     @Provides
 *     @IntoSet
 *     fun provideXxxGraph(): NavGraphContributor = NavGraphContributor { nc ->
 *         xxxGraph(nc)
 *     }
 * }
 * ```
 */
fun interface NavGraphContributor {

    /** 在壳层 NavHost 的 [NavGraphBuilder] 上注册本域全部 composable 路由。 */
    fun NavGraphBuilder.register(navController: NavHostController)
}

/**
 * 声明 Set 多绑定：图贡献者集允许为空（模块化初期 / lite flavor 可整域裁剪时无贡献者），
 * 无此声明 Dagger 会在根组件校验时报 MissingBinding。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavGraphContributorModule {

    @Multibinds
    abstract fun navGraphContributors(): Set<@JvmSuppressWildcards NavGraphContributor>
}
