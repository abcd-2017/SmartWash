package com.smartwash.feature.review.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.review.api.ReviewRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 记忆复习深链贡献者（@IntoSet 汇入壳层 DeepLinkRegistry）。
 * host "review" → 默认 ReviewRoute.Queue（今日复习队列页）；
 * 子路径 `smartwash://review/materials` → ReviewRoute.Materials（评审 P2：原实现忽略 path 段，
 * 外部入口无法直达资料页）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReviewDeepLinkModule {

    @Provides
    @IntoSet
    fun provideReviewDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "review" to DeepLinkRouteFactory { pathSegments, _ ->
                when (pathSegments.firstOrNull()?.lowercase()) {
                    "materials" -> ReviewRoute.Materials
                    else -> ReviewRoute.Queue
                }
            },
        )
    }
}
