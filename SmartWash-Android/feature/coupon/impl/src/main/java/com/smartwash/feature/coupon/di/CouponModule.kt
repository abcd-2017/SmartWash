package com.smartwash.feature.coupon.di

import com.smartwash.feature.coupon.api.CouponApi
import com.smartwash.feature.coupon.network.api.CouponServiceApi
import com.smartwash.feature.coupon.repository.CouponApiImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

/**
 * 优惠券域服务契约绑定（T7.2）：
 * - CouponApi → CouponApiImpl：跨模块消费方（payment-impl 支付页）经接口访问
 *   订单可用优惠券，替换原 payment-impl 的 CouponSource 过渡 seam 与 app 壳
 *   PaymentImplSeamModule 的实现（均已删）；
 * - CouponServiceApi 实例随优惠券域迁入（自 app/di 的 NetworkModule 拆出，
 *   模式同 laundry 的 LaundryApiModule / payment-impl 的 PaymentApiModule）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CouponModule {

    @Binds
    abstract fun bindCouponApi(impl: CouponApiImpl): CouponApi
}

/** 优惠券域网络供给 */
@Module
@InstallIn(SingletonComponent::class)
object CouponApiModule {

    @Provides
    fun provideCouponServiceApi(retrofit: Retrofit): CouponServiceApi =
        retrofit.create(CouponServiceApi::class.java)
}
