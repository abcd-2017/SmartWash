package com.smartwash.lite.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.lite.ui.liteGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * lite 变体导航图贡献者（flavor 源集专属，仅 lite classpath 存在）。
 *
 * 与 full 的 `*GraphModule`（app main 的 ShellGraphModule + 各域 impl 的 GraphModule）
 * 经 Hilt `@IntoSet` 汇入同一 [NavGraphContributor] 集合;注册策略与冲突处理见
 * [com.smartwash.lite.ui.liteGraph] 文件注释。
 */
@Module
@InstallIn(SingletonComponent::class)
object LiteGraphModule {

    @Provides
    @IntoSet
    fun provideLiteGraph(): NavGraphContributor = NavGraphContributor { nc ->
        liteGraph(nc)
    }
}
