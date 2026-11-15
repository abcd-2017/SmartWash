package com.smartwash.feature.laundry.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.laundry.ui.laundryGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 洗衣域图贡献者（服务目录 tab 页 / 洗衣预约页）。 */
@Module
@InstallIn(SingletonComponent::class)
object LaundryGraphModule {

    @Provides
    @IntoSet
    fun provideLaundryGraph(): NavGraphContributor = NavGraphContributor { nc ->
        laundryGraph(nc)
    }
}
