package com.smartwash.feature.divination.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.divination.DivRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 观象台深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - divination → DivRoute.Home（观象台首页）
 * - divination-history → DivRoute.History（卦历案卷）
 */
@Module
@InstallIn(SingletonComponent::class)
object DivinationDeepLinkModule {

    @Provides
    @IntoSet
    fun provideDivinationDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "divination" to DeepLinkRouteFactory { _, _ -> DivRoute.Home.text },
            "divination-history" to DeepLinkRouteFactory { _, _ -> DivRoute.History.text },
        )
    }
}
