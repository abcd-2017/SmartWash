package com.smartwash.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_SCREEN_MS
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.ai.AiWorkPage
import com.smartwash.ui.page.detail.OrderDetailPage
import com.smartwash.ui.page.home.HomePage
import com.smartwash.ui.page.pickup.PickupDeliveryPage
import com.smartwash.ui.page.pickup.PickupPage
import com.smartwash.ui.page.webview.WebViewPage

/**
 * 壳层留守页面路由聚合（T8.1）：主页壳（HomePage）+ 订单详情/寄件取件/取件/AI 工具台/
 * 内置浏览器五页面（T6.1/T7.1 调研结论：页面留 app 壳）的 composable 注册，自 MainActivity
 * NavHost 迁入。路由取值：主页壳/内置浏览器走 common:ui 的 [ShellRoute] 壳层契约；
 * 订单详情/寄件取件语义归订单域，走 order-api 的 [OrderRoute]；取件页/AI 工具台为
 * 壳层自有常量（[PageConstant.Pickup] / [PageConstant.AiWork]）。
 */
fun NavGraphBuilder.shellGraph(navController: NavHostController) {
    composable(ShellRoute.HOME) {
        HomePage(navController)
    }
    composable(
        route = "${OrderRoute.OrderDetail.text}/{orderId}",
        arguments = listOf(
            navArgument("orderId") {
                type = NavType.LongType
                defaultValue = -1
            }
        )
    ) { entity ->
        OrderDetailPage(
            navController,
            entity.arguments?.getLong("orderId") ?: -1
        )
    }
    composable(
        route = "${OrderRoute.PickupDelivery.text}/{orderId}/{pickupType}",
        arguments = listOf(
            navArgument("orderId") {
                type = NavType.LongType
                defaultValue = -1L
            }, navArgument("pickupType") {
                type = NavType.IntType
                defaultValue = 0
            }
        )
    ) { entity ->
        PickupDeliveryPage(
            entity.arguments?.getInt("pickupType") ?: 0,
            navController,
            entity.arguments?.getLong("orderId") ?: -1L
        )
    }
    composable(PageConstant.Pickup.text) {
        PickupPage(navController)
    }
    composable(PageConstant.AiWork.text) {
        AiWorkPage(onBack = { navController.popBackStack() })
    }
    // 内置浏览器页：url 必填（仅 http/https，非法值页面内展示错误态）、title 可选；
    // 字符串参数一律经 Uri.encode 编码（完整 URL 含 / ? & 等保留字），模式同 Toolbox 详情页
    composable(
        route = "${PageConstant.WebView.text}?url={url}&title={title}",
        arguments = listOf(
            navArgument("url") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("title") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entity ->
        WebViewPage(
            navController,
            url = entity.arguments?.getString("url").orEmpty(),
            title = entity.arguments?.getString("title").orEmpty(),
        )
    }
}

/**
 * 壳层全局出场淡出时长 — 动效语言 v0.4：退出 ≈ 进入的 70%（400ms → 280ms）。
 * 由 vocabulary 派生，避免在页面里写裸时长。
 */
private val SHELL_EXIT_FADE_MS = DURATION_SCREEN_MS * 7 / 10

/**
 * 壳层全局入场转场（原 MainActivity NavHost 内联动画，T8.1 抽出）。
 *
 * 动效语言 v0.4：fade 走页面转场档 400ms 减速 tween——alpha 用 `defaultSpring()`
 * 实际要 450–500ms，超出页面转场预算；scale 位移分量保留弹簧（从当前值动画，
 * 天然可中断）。reduceMotion 时取消缩放与长淡入，只留 180ms 纯淡入（规范 §8）。
 */
internal fun shellEnterTransition(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeIn(animationSpec = enterTween(DURATION_SCREEN_MS)) + scaleIn(
            initialScale = 0.95f,
            animationSpec = defaultSpring()
        )
    }

/** 壳层全局出场转场（pop 转场复用同值动画：fade 加速 280ms，scale 弹簧） */
internal fun shellExitTransition(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeOut(animationSpec = exitTween(SHELL_EXIT_FADE_MS)) + scaleOut(
            targetScale = 0.95f,
            animationSpec = defaultSpring()
        )
    }
