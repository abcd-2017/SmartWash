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
import com.smartwash.common.utils.defaultSpring
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.detail.OrderDetailPage
import com.smartwash.ui.page.home.HomePage
import com.smartwash.ui.page.pickup.PickupDeliveryPage
import com.smartwash.ui.page.pickup.PickupPage

/**
 * 壳层留守页面路由聚合（T8.1）：主页壳（HomePage）+ 订单详情/寄件取件/取件三页面
 * （T6.1/T7.1 调研结论：页面留 app 壳）的 composable 注册，自 MainActivity NavHost
 * 迁入。路由取值：主页壳走 common:ui 的 [ShellRoute] 壳层契约；订单详情/寄件取件
 * 语义归订单域，走 order-api 的 [OrderRoute]；取件页为壳层自有常量
 * （[PageConstant.Pickup]）。
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
}

/** 壳层全局入场转场（原 MainActivity NavHost 内联动画，T8.1 抽出；reduceMotion 时降级短 tween） */
internal fun shellEnterTransition(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(animationSpec = tween(200))
    } else {
        fadeIn(animationSpec = defaultSpring()) + scaleIn(
            initialScale = 0.95f,
            animationSpec = defaultSpring()
        )
    }

/** 壳层全局出场转场（pop 转场复用同值动画） */
internal fun shellExitTransition(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(animationSpec = tween(200))
    } else {
        fadeOut(animationSpec = defaultSpring()) + scaleOut(
            targetScale = 0.95f,
            animationSpec = defaultSpring()
        )
    }
