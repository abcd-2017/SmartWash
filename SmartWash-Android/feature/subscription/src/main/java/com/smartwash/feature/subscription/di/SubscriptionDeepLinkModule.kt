package com.smartwash.feature.subscription.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.subscription.SubscriptionRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 订阅逃生舱深链贡献者（@IntoSet 汇入壳层 DeepLinkRegistry）。
 * host "subscription" → SubscriptionRoute.List（订阅列表页）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SubscriptionDeepLinkModule {

    @Provides
    @IntoSet
    fun provideSubscriptionDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "subscription" to DeepLinkRouteFactory { _, _ -> SubscriptionRoute.List },
        )
    }
}
