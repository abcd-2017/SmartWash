package com.smartwash.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.deeplink.DeepLinkRegistry
import com.smartwash.deeplink.DeepLinkRepository
import com.smartwash.deeplink.DeepLinkRepositoryImpl
import com.smartwash.network.api.DeepLinkApi
import com.smartwash.ui.page.PageConstant
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 深链 Hilt 供给（壳层聚合点）：
 * - DeepLinkApi 复用 :common:network 注入的 Retrofit（baseUrl 走主网关 BuildConfig.BASE_URL，
 *   错误转译拦截器自动生效；公开接口不消费 @RequireAuthorization）；
 * - DeepLinkRepository 接口绑定；
 * - 壳层自有页面的深链贡献者（pickup / ai-work）经 @IntoSet 汇入——各域 impl 模块
 *   各自贡献本域 host（对齐 InitTask 模式），[DeepLinkRegistry] 合并；
 * - DeepLinkHandler / DeepLinkRegistry 为 @Singleton @Inject 构造，无需模块登记。
 */
@Module
@InstallIn(SingletonComponent::class)
object DeepLinkApiModule {

    @Provides
    @Singleton
    fun provideDeepLinkApi(retrofit: Retrofit): DeepLinkApi =
        retrofit.create(DeepLinkApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeepLinkRepositoryModule {

    @Binds
    abstract fun bindDeepLinkRepository(impl: DeepLinkRepositoryImpl): DeepLinkRepository
}

/**
 * 壳层自有留守页面的深链贡献者：pickup（取件页）/ ai-work（AI 工具台）。
 * host 字符串就地定义；路由串取壳层 [PageConstant] 单一事实来源。
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellDeepLinkModule {

    @Provides
    @IntoSet
    fun provideShellDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "pickup" to DeepLinkRouteFactory { _, _ -> PageConstant.Pickup.text },
            "ai-work" to DeepLinkRouteFactory { _, _ -> PageConstant.AiWork.text },
        )
    }
}
