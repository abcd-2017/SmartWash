package com.smartwash.feature.dining.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.dining.ui.diningGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 吃啥（家庭点单）域图贡献者（订单六页 + 菜谱三页）。 */
@Module
@InstallIn(SingletonComponent::class)
object DiningGraphModule {

    @Provides
    @IntoSet
    fun provideDiningGraph(): NavGraphContributor = NavGraphContributor { nc ->
        diningGraph(nc)
    }
}
