package com.smartwash.feature.game.di

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import com.smartwash.feature.game.GameRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 游戏域深链贡献者（对齐 common:init 的 InitTask 模式：@IntoSet 汇入壳层
 * DeepLinkRegistry 的贡献者集合，host 字符串就地定义）。
 *
 * 本域 host：
 * - game-center → GameRoute.Center（游戏中心列表页；后端 workbench_entry 卡片
 *   target 即本深链，方案 §三字典种子同步登记）
 */
@Module
@InstallIn(SingletonComponent::class)
object GameDeepLinkModule {

    @Provides
    @IntoSet
    fun provideGameDeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "game-center" to DeepLinkRouteFactory { _, _ -> GameRoute.Center.text },
        )
    }
}
