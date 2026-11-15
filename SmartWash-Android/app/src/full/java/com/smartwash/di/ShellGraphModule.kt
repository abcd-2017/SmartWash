package com.smartwash.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.ui.navigation.shellGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 壳层留守页面图贡献者：主页壳 / 订单详情 / 寄件取件 / 取件 / 全局扫一扫 / 内置浏览器。
 * 对齐各域 impl 的 GraphModule（@Provides @IntoSet 汇入 NavGraphContributor 集合）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellGraphModule {

    @Provides
    @IntoSet
    fun provideShellGraph(): NavGraphContributor = NavGraphContributor { nc ->
        shellGraph(nc)
    }
}
