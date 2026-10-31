package com.smartwash.feature.coupon.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.coupon.api.CouponRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 优惠券域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - coupon → CouponRoute.Coupon
 */
@Module
@InstallIn(SingletonComponent::class)
object CouponDeepLinkModule {

    @Provides
    @IntoSet
    fun provideCouponDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "coupon" to DeepLinkRouteFactory { _, _ -> CouponRoute.Coupon.text },
        )
    }
}
