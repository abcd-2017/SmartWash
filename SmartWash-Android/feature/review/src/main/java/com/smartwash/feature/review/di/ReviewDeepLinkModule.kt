package com.smartwash.feature.review.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.review.ReviewRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 记忆复习深链贡献者（@IntoSet 汇入壳层 DeepLinkRegistry）。
 * host "review" → ReviewRoute.Queue（今日复习队列页）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReviewDeepLinkModule {

    @Provides
    @IntoSet
    fun provideReviewDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "review" to DeepLinkRouteFactory { _, _ -> ReviewRoute.Queue },
        )
    }
}
