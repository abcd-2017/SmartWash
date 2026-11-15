package com.smartwash.feature.toolbox.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.toolbox.ui.delivery.deliveryGraph
import com.smartwash.feature.toolbox.ui.toolboxGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 工具箱图贡献者：短链/资产标签（[toolboxGraph]）与设备投递（[deliveryGraph]）。 */
@Module
@InstallIn(SingletonComponent::class)
object ToolboxGraphModule {

    @Provides
    @IntoSet
    fun provideToolboxGraph(): NavGraphContributor = NavGraphContributor { nc ->
        toolboxGraph(nc)
    }

    @Provides
    @IntoSet
    fun provideDeliveryGraph(): NavGraphContributor = NavGraphContributor { nc ->
        deliveryGraph(nc)
    }
}
