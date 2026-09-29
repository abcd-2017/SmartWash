package com.smartwash.feature.payment.impl.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.common.utils.momentumSpring
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.feature.payment.impl.ui.payment.PaySuccessPage
import com.smartwash.feature.payment.impl.ui.payment.PaymentPage
import com.smartwash.feature.payment.impl.ui.recharge.RechargePage
import com.smartwash.feature.payment.impl.ui.recharge.RechargeRecordPage

/**
 * 支付域路由聚合（T8.1）：本域 4 页面的 composable 注册自 app 壳 MainActivity
 * NavHost 迁入，宿主侧一行 [paymentGraph] 完成聚合。路由常量取 payment-api 的
 * [PaymentRoute]（单一事实来源）。支付/支付成功页保持原底部滑入专属转场
 * （reduceMotion 时降级短 tween）。
 */
fun NavGraphBuilder.paymentGraph(navController: NavHostController, reduceMotion: Boolean) {
    composable(PaymentRoute.Recharge.text) {
        RechargePage(navController)
    }
    composable(PaymentRoute.RechargeRecord.text) {
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

/** 底部滑入 + 淡入（原 MainActivity 内联转场；reduceMotion 时降级短 tween） */
private fun slideEnter(reduceMotion: Boolean): EnterTransition =
    slideInVertically(
        initialOffsetY = { (it * 0.3f).toInt() },
        animationSpec = if (reduceMotion) tween(250) else momentumSpring()
    ) + fadeIn(animationSpec = if (reduceMotion) tween(250) else momentumSpring())

/** 底部滑出 + 淡出（原 MainActivity 内联转场） */
private fun slideExit(reduceMotion: Boolean): ExitTransition =
    slideOutVertically(
        targetOffsetY = { (it * 0.3f).toInt() },
        animationSpec = if (reduceMotion) tween(250) else momentumSpring()
    ) + fadeOut(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
