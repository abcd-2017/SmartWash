package com.smartwash.di

import com.smartwash.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * app 侧网络装配（T2.3 自 RetrofitClient 拆出）：
 * - baseUrl：环境地址由 app 的 BuildConfig 注入，common:network 的 Retrofit 零配置感知
 * - 业务 Retrofit 接口实例（AppUpdateApi 在 UpdateModule、DivinationApi 在 DivinationModule、
 *   UserAccountApi 已随用户域迁 :feature:user:impl 的 UserAccountApiModule，T5.2；
 *   OrderServiceApi 已随订单域迁 :feature:order:impl 的 OrderServiceApiModule，T6.1；
 *   PaymentApi/RechargeApi 已随支付+充值域迁 :feature:payment:impl 的 PaymentApiModule，T6.2；
 *   LaundryItemsApi/SchoolApi 已随洗衣+学校域迁 :feature:laundry:impl 的 LaundryApiModule，T7.1；
 *   CouponServiceApi 已随优惠券域迁 :feature:coupon:impl 的 CouponApiModule，T7.2）
 */
@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.BASE_URL
}
