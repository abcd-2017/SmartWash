package com.smartwash.feature.order.impl.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.order.impl.ui.orderGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 订单域图贡献者（订单列表五 Tab 页）。 */
@Module
@InstallIn(SingletonComponent::class)
object OrderGraphModule {

    @Provides
    @IntoSet
    fun provideOrderGraph(): NavGraphContributor = NavGraphContributor { nc ->
        orderGraph(nc)
    }
}
