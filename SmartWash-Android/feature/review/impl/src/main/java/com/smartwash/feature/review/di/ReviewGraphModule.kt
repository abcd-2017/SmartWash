package com.smartwash.feature.review.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.review.ui.reviewGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 记忆复习图贡献者（今日队列/复习/资料三页）。 */
@Module
@InstallIn(SingletonComponent::class)
object ReviewGraphModule {

    @Provides
    @IntoSet
    fun provideReviewGraph(): NavGraphContributor = NavGraphContributor { nc ->
        reviewGraph(nc)
    }
}
