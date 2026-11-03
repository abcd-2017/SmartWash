package com.smartwash.feature.toolbox.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.toolbox.ToolboxRoute
import com.smartwash.feature.toolbox.network.ShortCodeVo
import com.smartwash.feature.toolbox.ui.create.ShortCodeCreatePage
import com.smartwash.feature.toolbox.ui.detail.ShortCodeDetailPage
import com.smartwash.feature.toolbox.ui.list.ToolboxListPage

/**
 * 工具箱路由聚合（模式同 divinationGraph）：3 页面的 composable 注册自 app 壳
 * MainActivity NavHost，宿主侧一行 [toolboxGraph] 完成聚合。类型安全路由取模块内
 * [ToolboxRoute]（单一事实来源）。详情页参数由 SavedStateHandle 读取（无需 toRoute）。
 * 当前无工作台入口，接入见对接文档 TODO 节。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.toolboxGraph(
    navController: NavHostController,
    // 一镜到底：共享元素 scope（2026-11-01）
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<ToolboxRoute.List> {
        // 一镜到底：NavHost 条目 scope 下传，供列表卡 sharedElement（2026-11-01）
        ToolboxListPage(
            navController,
            animatedVisibilityScope = this,
            sharedTransitionScope = sharedTransitionScope,
        )
    }
    composable<ToolboxRoute.Create> {
        ShortCodeCreatePage(navController)
    }
    composable<ToolboxRoute.Detail> {
        // 一镜到底：NavHost 条目 scope 下传，供详情卡 sharedElement（2026-11-01）
        ShortCodeDetailPage(
            navController,
            animatedVisibilityScope = this,
            sharedTransitionScope = sharedTransitionScope,
        )
    }
}

/** 列表 → 详情：基础字段随路由携带（勿在详情页经 resolve 拉取，resolve 会计点击） */
fun NavHostController.navigateToShortCodeDetail(vo: ShortCodeVo) {
    navigate(
        ToolboxRoute.Detail(
            id = vo.id,
            code = vo.code,
            contentType = vo.contentType,
            isPublic = vo.isPublic,
            clickCount = vo.clickCount,
            target = vo.target.orEmpty(),
            expireAt = vo.expireAt.orEmpty(),
            shortUrl = vo.shortUrl.orEmpty(),
            createdAt = vo.createdAt.orEmpty(),
        )
    )
}

/** 创建/续期/删除成功后置位刷新信号（列表页消费后复位并重建分页流） */
fun NavHostController.notifyShortCodeChanged() {
    previousBackStackEntry?.savedStateHandle?.set(ToolboxRoute.CHANGED_KEY, true)
}
