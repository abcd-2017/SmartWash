package com.smartwash.feature.user.impl.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.ui.login.LoginPage
import com.smartwash.feature.user.impl.ui.register.RegisterPage
import com.smartwash.feature.user.impl.ui.setting.SettingPage
import com.smartwash.feature.user.impl.ui.update_userinfo.UpdateUserInfoPage

/** 退出时长 — 动效语言 v0.4：退出 ≈ 进入的 70%（由 vocabulary 派生，不写裸时长） */
private val AUTH_EXIT_MS = DURATION_MICRO_MS * 7 / 10

/**
 * 用户域路由聚合（T8.1）：本域 4 页面的 composable 注册自 app 壳 MainActivity
 * NavHost 迁入，宿主侧一行 [userGraph] 完成聚合。路由常量取 user-api 的
 * [UserRoute]（单一事实来源）。
 *
 * 登录 ↔ 注册带方向语义（T8.1 后补充）：前进（登录 → 注册）新页自右侧 1/12 屏宽
 * 滑入 + 淡入、旧页向左侧滑出；返回（注册 → 登录）全部反向。位移用
 * `defaultSpring()`（可中断），淡入淡出走卡片级/微交互级 tween。
 * reduceMotion 时位移归零，四个方向统一 180ms 纯淡入淡出（规范 §8）。
 *
 * @param reduceMotion 全应用动效降级开关（壳层 MainActivity 提供，与 NavHost 全局转场同源）
 * @param checkUpdateContent 设置页「检查更新」行插槽（feature:update 归壳层聚合，
 *   user-impl 不依赖它；壳层经参数注入，T5.2 插槽模式保持不变）
 */
fun NavGraphBuilder.userGraph(
    navController: NavHostController,
    reduceMotion: Boolean,
    checkUpdateContent: (@Composable () -> Unit)? = null,
) {
    composable(
        UserRoute.Login.text,
        // 前进离场：向左滑出（与 Register 的右侧滑入同一方向语义）；
        // 返回入场：自左侧滑回。降级时纯淡入淡出
        exitTransition = { forwardExit(reduceMotion) },
        popEnterTransition = { backwardEnter(reduceMotion) },
    ) {
        LoginPage(navController)
    }
    composable(
        UserRoute.Register.text,
        enterTransition = { forwardEnter(reduceMotion) },
        popEnterTransition = { forwardEnter(reduceMotion) },
        exitTransition = { forwardExit(reduceMotion) },
        popExitTransition = { backwardExit(reduceMotion) },
    ) {
        RegisterPage(navController)
    }
    composable(UserRoute.UpdateUserInfo.text) {
        UpdateUserInfoPage(navController)
    }
    composable(UserRoute.Setting.text) {
        SettingPage(navController, checkUpdateContent)
    }
}

/**
 * 前进入场：自右侧 1/12 屏宽滑入 + 卡片级 300ms 减速淡入。
 * reduceMotion 时位移归零，只留 180ms 纯淡入。
 */
private fun forwardEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeIn(animationSpec = enterTween(DURATION_CARD_MS)) +
            slideInHorizontally(initialOffsetX = { it / 12 }, animationSpec = defaultSpring())
    }

/** 前进离场：向左侧滑出 + 加速淡出（进入时长的 70%）；reduceMotion 时纯淡出 */
private fun forwardExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeOut(animationSpec = exitTween(AUTH_EXIT_MS)) +
            slideOutHorizontally(targetOffsetX = { -it / 12 }, animationSpec = defaultSpring())
    }

/** 返回入场：自左侧 1/12 屏宽滑回 + 卡片级淡入（前进的镜像）；reduceMotion 时纯淡入 */
private fun backwardEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeIn(animationSpec = enterTween(DURATION_CARD_MS)) +
            slideInHorizontally(initialOffsetX = { -it / 12 }, animationSpec = defaultSpring())
    }

/** 返回离场：向右侧滑出 + 加速淡出（前进离场的镜像）；reduceMotion 时纯淡出 */
private fun backwardExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(animationSpec = tween(CROSSFADE_ALT_MS))
    } else {
        fadeOut(animationSpec = exitTween(AUTH_EXIT_MS)) +
            slideOutHorizontally(targetOffsetX = { it / 12 }, animationSpec = defaultSpring())
    }
