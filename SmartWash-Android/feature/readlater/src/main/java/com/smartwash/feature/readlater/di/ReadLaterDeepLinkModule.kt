package com.smartwash.feature.readlater.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.readlater.ReadLaterRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 稍后读深链贡献者（@IntoSet 汇入壳层 DeepLinkRegistry，模式同 mood/toolbox）。
 * host "read-later" → ReadLaterRoute.Page（稍后读列表页）。
 * 本页消费 /web/auth 鉴权接口，属受保护深链（未登录由壳层落登录页，不进公开白名单）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReadLaterDeepLinkModule {

    @Provides
    @IntoSet
    fun provideReadLaterDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "read-later" to DeepLinkRouteFactory { _, _ -> ReadLaterRoute.Page },
        )
    }
}
