package com.smartwash.feature.lab.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.lab.LabRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 解压实验舱深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - lab → LabRoute.Home（实验舱首页；后端 workbench_entry 卡片
 *   target 即本深链，smart_wash.sql 字典种子同步登记）
 */
@Module
@InstallIn(SingletonComponent::class)
object LabDeepLinkModule {

    @Provides
    @IntoSet
    fun provideLabDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "lab" to DeepLinkRouteFactory { _, _ -> LabRoute.Home.text },
        )
    }
}
