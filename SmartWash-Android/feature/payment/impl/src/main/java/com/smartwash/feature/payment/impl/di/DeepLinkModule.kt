package com.smartwash.feature.payment.impl.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.payment.api.PaymentRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 支付/充值域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - recharge → PaymentRoute.Recharge
 * - recharge-record → PaymentRoute.RechargeRecord
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentDeepLinkModule {

    @Provides
    @IntoSet
    fun providePaymentDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "recharge" to DeepLinkRouteFactory { _, _ -> PaymentRoute.Recharge.text },
            "recharge-record" to DeepLinkRouteFactory { _, _ -> PaymentRoute.RechargeRecord.text },
        )
    }
}
