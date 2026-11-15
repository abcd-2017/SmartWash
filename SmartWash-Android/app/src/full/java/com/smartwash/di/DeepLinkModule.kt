package com.smartwash.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.deeplink.DeepLinkRegistry
import com.smartwash.ui.page.PageConstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 壳层深链贡献者（full 变体）：pickup（取件页）/ ai-work（AI 工具台）。
 * host 字符串就地定义；类型安全路由取壳层 [PageConstant] 单一事实来源。
 *
 * 深链 Hilt 供给（DeepLinkApi / DeepLinkRepository 绑定）在 **main 源集**
 * （两变体共用，lite 也消费）；[DeepLinkHandler] / [DeepLinkRegistry] 为
 * @Singleton @Inject 构造，无需模块登记。
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellDeepLinkModule {

    @Provides
    @IntoSet
    fun provideShellDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "pickup" to DeepLinkRouteFactory { _, _ -> PageConstant.Pickup },
            "ai-work" to DeepLinkRouteFactory { _, _ -> PageConstant.AiWork },
        )
    }
}
