package com.smartwash.feature.coupon.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.coupon.ui.couponGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 优惠券域图贡献者（三 Tab 优惠券页）。 */
@Module
@InstallIn(SingletonComponent::class)
object CouponGraphModule {

    @Provides
    @IntoSet
    fun provideCouponGraph(): NavGraphContributor = NavGraphContributor { nc ->
        couponGraph(nc)
    }
}
