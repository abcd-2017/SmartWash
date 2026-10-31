package com.smartwash.feature.laundry.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.laundry.api.LaundryRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 洗衣域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - laundry → LaundryRoute.Laundry（洗衣预约页）
 *
 * service 不注册：Service 是 HomePage 底部 tab 页（HorizontalPager 内），
 * 跳转等价「navigate(Home) 并切 pager」，需改 ShellRoute.HOME 注册形态并让
 * HomePage 观察初始 tab，不满足低成本标准（原因同步记录于 app/AGENTS.md deeplink 章节）。
 */
@Module
@InstallIn(SingletonComponent::class)
object LaundryDeepLinkModule {

    @Provides
    @IntoSet
    fun provideLaundryDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "laundry" to DeepLinkRouteFactory { _, _ -> LaundryRoute.Laundry.text },
        )
    }
}
