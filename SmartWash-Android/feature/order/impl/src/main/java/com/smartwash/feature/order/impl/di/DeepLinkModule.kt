package com.smartwash.feature.order.impl.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.order.api.model.ShowOrderStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 订单域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - order → "Order/{tab}"（OrderRoute.Order 注册形态，tab 为初始 tab：
 *   query `tab` 缺省 0，防御夹取 0..ShowOrderStatus.size-1）
 */
@Module
@InstallIn(SingletonComponent::class)
object OrderDeepLinkModule {

    @Provides
    @IntoSet
    fun provideOrderDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "order" to DeepLinkRouteFactory { _, query ->
                // tab 非法/缺省回 0；越界防御夹取（与 OrderPage 入参 coerce 口径一致）
                val tab = (query["tab"]?.trim()?.toIntOrNull() ?: 0)
                    .coerceIn(0, ShowOrderStatus.entries.size - 1)
                "${OrderRoute.Order.text}/$tab"
            },
        )
    }
}
