package com.smartwash.feature.toolbox.ui

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.feature.toolbox.ToolboxRoute
import com.smartwash.feature.toolbox.network.ShortCodeVo
import com.smartwash.feature.toolbox.ui.create.ShortCodeCreatePage
import com.smartwash.feature.toolbox.ui.detail.ShortCodeDetailPage
import com.smartwash.feature.toolbox.ui.detail.ShortCodeDetailViewModel
import com.smartwash.feature.toolbox.ui.list.ToolboxListPage

/**
 * 工具箱路由聚合（模式同 divinationGraph）：3 页面的 composable 注册自 app 壳
 * MainActivity NavHost，宿主侧一行 [toolboxGraph] 完成聚合。路由常量取模块内
 * [ToolboxRoute]（单一事实来源）。当前无工作台入口，接入见对接文档 TODO 节。
 */
fun NavGraphBuilder.toolboxGraph(navController: NavHostController) {
    composable(ToolboxRoute.List.text) {
        ToolboxListPage(navController)
    }
    composable(ToolboxRoute.Create.text) {
        ShortCodeCreatePage(navController)
    }
    // 字符串参数一律走 query 并经 Uri.encode 编码（target 为完整 URL，含 / ? & 等保留字）
    composable(
        route = "${ToolboxRoute.Detail.text}" +
            "?${ShortCodeDetailViewModel.ARG_ID}={${ShortCodeDetailViewModel.ARG_ID}}" +
            "&${ShortCodeDetailViewModel.ARG_CODE}={${ShortCodeDetailViewModel.ARG_CODE}}" +
            "&${ShortCodeDetailViewModel.ARG_CONTENT_TYPE}={${ShortCodeDetailViewModel.ARG_CONTENT_TYPE}}" +
            "&${ShortCodeDetailViewModel.ARG_IS_PUBLIC}={${ShortCodeDetailViewModel.ARG_IS_PUBLIC}}" +
            "&${ShortCodeDetailViewModel.ARG_CLICK_COUNT}={${ShortCodeDetailViewModel.ARG_CLICK_COUNT}}" +
            "&${ShortCodeDetailViewModel.ARG_TARGET}={${ShortCodeDetailViewModel.ARG_TARGET}}" +
            "&${ShortCodeDetailViewModel.ARG_EXPIRE_AT}={${ShortCodeDetailViewModel.ARG_EXPIRE_AT}}" +
            "&${ShortCodeDetailViewModel.ARG_SHORT_URL}={${ShortCodeDetailViewModel.ARG_SHORT_URL}}" +
            "&${ShortCodeDetailViewModel.ARG_CREATED_AT}={${ShortCodeDetailViewModel.ARG_CREATED_AT}}",
        arguments = listOf(
            navArgument(ShortCodeDetailViewModel.ARG_ID) {
                type = NavType.LongType
                defaultValue = -1L
            },
            navArgument(ShortCodeDetailViewModel.ARG_CODE) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(ShortCodeDetailViewModel.ARG_CONTENT_TYPE) {
                type = NavType.IntType
                defaultValue = 1
            },
            navArgument(ShortCodeDetailViewModel.ARG_IS_PUBLIC) {
                type = NavType.IntType
                defaultValue = 1
            },
            navArgument(ShortCodeDetailViewModel.ARG_CLICK_COUNT) {
                type = NavType.LongType
                defaultValue = 0L
            },
            navArgument(ShortCodeDetailViewModel.ARG_TARGET) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(ShortCodeDetailViewModel.ARG_EXPIRE_AT) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(ShortCodeDetailViewModel.ARG_SHORT_URL) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(ShortCodeDetailViewModel.ARG_CREATED_AT) {
                type = NavType.StringType
                defaultValue = ""
            },
        )
    ) {
        ShortCodeDetailPage(navController)
    }
}

/** 列表 → 详情：基础字段随路由携带（勿在详情页经 resolve 拉取，resolve 会计点击） */
fun NavHostController.navigateToShortCodeDetail(vo: ShortCodeVo) {
    navigate(
        "${ToolboxRoute.Detail.text}" +
            "?${ShortCodeDetailViewModel.ARG_ID}=${vo.id}" +
            "&${ShortCodeDetailViewModel.ARG_CODE}=${Uri.encode(vo.code)}" +
            "&${ShortCodeDetailViewModel.ARG_CONTENT_TYPE}=${vo.contentType}" +
            "&${ShortCodeDetailViewModel.ARG_IS_PUBLIC}=${vo.isPublic}" +
            "&${ShortCodeDetailViewModel.ARG_CLICK_COUNT}=${vo.clickCount}" +
            "&${ShortCodeDetailViewModel.ARG_TARGET}=${Uri.encode(vo.target.orEmpty())}" +
            "&${ShortCodeDetailViewModel.ARG_EXPIRE_AT}=${Uri.encode(vo.expireAt.orEmpty())}" +
            "&${ShortCodeDetailViewModel.ARG_SHORT_URL}=${Uri.encode(vo.shortUrl.orEmpty())}" +
            "&${ShortCodeDetailViewModel.ARG_CREATED_AT}=${Uri.encode(vo.createdAt.orEmpty())}",
    )
}

/** 创建/续期/删除成功后置位刷新信号（列表页消费后复位并重建分页流） */
fun NavHostController.notifyShortCodeChanged() {
    previousBackStackEntry?.savedStateHandle?.set(ToolboxRoute.CHANGED_KEY, true)
}
