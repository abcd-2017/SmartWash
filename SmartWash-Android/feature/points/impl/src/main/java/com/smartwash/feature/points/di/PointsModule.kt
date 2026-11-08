package com.smartwash.feature.points.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.points.api.PointsRoute
import com.smartwash.feature.points.api.PointsServiceApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 积分域网络供给：PointsServiceApi 实例（模式同 coupon 的 CouponApiModule /
 * laundry 的 LaundryApiModule——Retrofit 实例由 common:network 的 NetworkModule 提供）。
 */
@Module
@InstallIn(SingletonComponent::class)
object PointsApiModule {

    @Provides
    @Singleton
    fun providePointsServiceApi(retrofit: Retrofit): PointsServiceApi =
        retrofit.create(PointsServiceApi::class.java)
}

/**
 * 积分域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - points → PointsRoute.Home
 *
 * 登录守卫：积分页消费 /web/auth 鉴权接口，host 不进壳层 PUBLIC_DEEP_LINK_HOSTS
 * 白名单（未登录由壳层拦截落登录页，对齐 P-2001 受保护 host 默认语义）。
 */
@Module
@InstallIn(SingletonComponent::class)
object PointsDeepLinkModule {

    @Provides
    @IntoSet
    fun providePointsDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "points" to DeepLinkRouteFactory { _, _ -> PointsRoute.Home },
        )
    }
}
