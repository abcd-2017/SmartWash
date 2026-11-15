package com.smartwash.feature.game.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.game.ui.gameGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 游戏中心图贡献者（游戏中心页 + 游戏 H5 承载页）。 */
@Module
@InstallIn(SingletonComponent::class)
object GameGraphModule {

    @Provides
    @IntoSet
    fun provideGameGraph(): NavGraphContributor = NavGraphContributor { nc ->
        gameGraph(nc)
    }
}
