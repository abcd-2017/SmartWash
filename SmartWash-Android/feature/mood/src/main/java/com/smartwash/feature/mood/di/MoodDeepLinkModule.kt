package com.smartwash.feature.mood.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.mood.MoodRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 情绪气象站深链贡献者（@IntoSet 汇入壳层 DeepLinkRegistry）。
 * host "mood" → MoodRoute.Today（今日记录页）。
 */
@Module
@InstallIn(SingletonComponent::class)
object MoodDeepLinkModule {

    @Provides
    @IntoSet
    fun provideMoodDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "mood" to DeepLinkRouteFactory { _, _ -> MoodRoute.Today },
        )
    }
}
