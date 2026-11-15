package com.smartwash.feature.points.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.points.ui.pointsGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 积分域图贡献者（签到有礼页）。 */
@Module
@InstallIn(SingletonComponent::class)
object PointsGraphModule {

    @Provides
    @IntoSet
    fun providePointsGraph(): NavGraphContributor = NavGraphContributor { nc ->
        pointsGraph(nc)
    }
}
