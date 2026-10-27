package com.smartwash.feature.payment.impl.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.feature.payment.impl.ui.payment.PaySuccessPage
import com.smartwash.feature.payment.impl.ui.payment.PaymentPage
import com.smartwash.feature.payment.impl.ui.recharge.RechargePage
import com.smartwash.feature.payment.impl.ui.recharge.RechargeRecordPage

/**
 * 支付域路由聚合（T8.1）：本域 4 页面的 composable 注册自 app 壳 MainActivity
 * NavHost 迁入，宿主侧一行 [paymentGraph] 完成聚合。路由常量取 payment-api 的
 * [PaymentRoute]（单一事实来源）。本域 4 页面统一走「底部上滑 + 淡入」转场，
 * 进入用卡片级减速 tween、退出用进入时长 67% 的加速 tween；reduceMotion 时位移归零，
 * 只保留 180ms 淡入淡出（规范 §8）。
 */
fun NavGraphBuilder.paymentGraph(navController: NavHostController, reduceMotion: Boolean) {
    composable(
        PaymentRoute.Recharge.text,
        enterTransition = { slideEnter(reduceMotion) },
        exitTransition = { slideExit(reduceMotion) },
        popEnterTransition = { slideEnter(reduceMotion) },
        popExitTransition = { slideExit(reduceMotion) },
    ) {
        RechargePage(navController)
    }
    composable(
        PaymentRoute.RechargeRecord.text,
        enterTransition = { slideEnter(reduceMotion) },
        exitTransition = { slideExit(reduceMotion) },
        popEnterTransition = { slideEnter(reduceMotion) },
        popExitTransition = { slideExit(reduceMotion) },
    ) {
        RechargeRecordPage(navController)
    }
    composable(
        route = "${PaymentRoute.Payment.text}/{orderId}",
        arguments = listOf(
            navArgument("orderId") {
                type = NavType.LongType
                defaultValue = -1
            }
        ),
        enterTransition = { slideEnter(reduceMotion) },
        exitTransition = { slideExit(reduceMotion) },
        popEnterTransition = { slideEnter(reduceMotion) },
        popExitTransition = { slideExit(reduceMotion) },
    ) { entity ->
        PaymentPage(navController, entity.arguments?.getLong("orderId"))
    }
    composable(
        route = "${PaymentRoute.PaySuccess.text}/{orderId}",
        arguments = listOf(
            navArgument("orderId") {
                type = NavType.LongType
                defaultValue = -1
            }
        ),
        enterTransition = { slideEnter(reduceMotion) },
        exitTransition = { slideExit(reduceMotion) },
        popEnterTransition = { slideEnter(reduceMotion) },
        popExitTransition = { slideExit(reduceMotion) },
    ) { entity ->
        PaySuccessPage(navController, entity.arguments?.getLong("orderId") ?: -1)
    }
}

/**
 * 进入：底部上滑 1/3 屏高 + 淡入（卡片级 300ms 减速 tween）。
 * reduceMotion 时位移归零，只留 180ms 淡入（规范 §8：取消位移）。
 */
private fun slideEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = enterTween(DURATION_CARD_MS)
        ) + fadeIn(animationSpec = enterTween(DURATION_CARD_MS))
    }

/** 退出：淡出（进入时长的 67%，加速 tween）；reduceMotion 时 180ms 淡出 */
private fun slideExit(reduceMotion: Boolean): ExitTransition =
    fadeOut(
        animationSpec = if (reduceMotion) tween(CROSSFADE_ALT_MS)
        else exitTween(DURATION_MICRO_MS)
    )
