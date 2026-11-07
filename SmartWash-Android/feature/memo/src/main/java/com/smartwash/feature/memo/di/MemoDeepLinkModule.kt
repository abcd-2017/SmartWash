package com.smartwash.feature.memo.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.memo.MemoRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 速记手账深链贡献者（@IntoSet 汇入壳层 DeepLinkRegistry，模式对齐 MoodDeepLinkModule）。
 *
 * 本域 host：
 * - quick-memo → MemoRoute.Page（速记手账主页；后端 workbench_entry 卡片 target
 *   即本深链，字典种子由后端同步登记）。
 *
 * 受保护 host：本域全部接口走 @RequireAuthorization，壳层登录守卫默认拦截未登录访问
 * （host 不进 DeepLinkHandler 的 PUBLIC_DEEP_LINK_HOSTS 白名单）。
 */
@Module
@InstallIn(SingletonComponent::class)
object MemoDeepLinkModule {

    @Provides
    @IntoSet
    fun provideMemoDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "quick-memo" to DeepLinkRouteFactory { _, _ -> MemoRoute.Page },
        )
    }
}
