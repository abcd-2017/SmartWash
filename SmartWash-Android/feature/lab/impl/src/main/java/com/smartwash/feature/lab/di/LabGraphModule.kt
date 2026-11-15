package com.smartwash.feature.lab.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.lab.ui.labGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 解压实验舱图贡献者（首页 + 10 个纯本地玩具页）。 */
@Module
@InstallIn(SingletonComponent::class)
object LabGraphModule {

    @Provides
    @IntoSet
    fun provideLabGraph(): NavGraphContributor = NavGraphContributor { nc ->
        labGraph(nc)
    }
}
