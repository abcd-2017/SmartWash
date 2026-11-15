package com.smartwash.feature.subscription.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.subscription.ui.subscriptionGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 订阅逃生舱图贡献者（列表/编辑/详情/提醒四页）。 */
@Module
@InstallIn(SingletonComponent::class)
object SubscriptionGraphModule {

    @Provides
    @IntoSet
    fun provideSubscriptionGraph(): NavGraphContributor = NavGraphContributor { nc ->
        subscriptionGraph(nc)
    }
}
