package com.smartwash.feature.user.impl.di

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.common.ui.navigation.NavGraphAmbient
import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.ui.login.LoginPage
import com.smartwash.feature.user.impl.ui.register.RegisterPage
import com.smartwash.feature.user.impl.ui.setting.ApiConfigPage
import com.smartwash.feature.user.impl.ui.setting.SettingPage
import com.smartwash.feature.user.impl.ui.update_userinfo.UpdateUserInfoPage
import com.smartwash.feature.user.impl.ui.wallet.WalletTransactionsPage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 退出时长 — 动效语言 v0.4：退出 ≈ 进入的 70%（由 vocabulary 派生，不写裸时长） */
private val AUTH_EXIT_MS = DURATION_MICRO_MS * 7 / 10

/**
 * full 变体用户域认证/设置页图贡献者（flavor 源集专属，仅 full classpath 存在）。
 *
 * [UserRoute.Login] / [Register] / [Setting] 三条路由的页面实现分属两个变体——full 用
 * 本模块 src/full 的 LoginPage/RegisterPage/SettingPage，lite 用 app/src/lite 的
 * LiteLoginPage/LiteRegisterPage/LiteSettingPage。同路由重复注册在 Navigation 2.8 是
 * 「后注册覆盖」，胜出者取决于 Hilt `@IntoSet` 聚合顺序（未保证），故路由注册必须与
 * 页面实现同源、每变体唯一。userGraph 因此不再注册这三条，改由本模块（full）与
 * liteGraph（lite）各自注册。
 *
 * 同源集还承载另外三条 full 专属 user 页面的注册（资料编辑 / 解读模型 / 钱包流水）：
 * 其实现依赖 laundry-api / notice-api / payment-api 等 full 专属契约，故一并放
 * src/full（放进 src/main 会让 lite classpath 缺类）。
 *
 * 登录 ↔ 注册带方向语义：前进（登录 → 注册）新页自右侧 1/12 屏宽滑入 + 淡入、旧页向左侧
 * 滑出；返回（注册 → 登录）全部反向。位移用 `defaultSpring()`（可中断），淡入淡出走
 * 卡片级/微交互级 tween。reduceMotion 时位移归零，四个方向统一 180ms 纯淡入淡出（规范 §8）。
 * reduceMotion 在转场 lambda 内读取 [NavGraphAmbient.reduceMotion] 快照（NavHost builder
 * 非组合上下文，快照由壳层组合期写入）。
 */
@Module
@InstallIn(SingletonComponent::class)
object UserFlavorGraphModule {

    @Provides
    @IntoSet
    fun provideUserFlavorGraph(): NavGraphContributor = NavGraphContributor { nc ->
        userFlavorGraph(nc)
    }
}

/** full 变体用户域认证/设置三页注册（含认证转场），见 [UserFlavorGraphModule] 注释 */
fun NavGraphBuilder.userFlavorGraph(navController: NavHostController) {
    composable<UserRoute.Login>(
        // 前进离场：向左滑出（与 Register 的右侧滑入同一方向语义）；
        // 返回入场：自左侧滑回。降级时纯淡入淡出
        exitTransition = { forwardExit(NavGraphAmbient.reduceMotion) },
        popEnterTransition = { backwardEnter(NavGraphAmbient.reduceMotion) },
    ) {
        LoginPage(navController)
    }
    composable<UserRoute.Register>(
        enterTransition = { forwardEnter(NavGraphAmbient.reduceMotion) },
        popEnterTransition = { forwardEnter(NavGraphAmbient.reduceMotion) },
        exitTransition = { forwardExit(NavGraphAmbient.reduceMotion) },
        popExitTransition = { backwardExit(NavGraphAmbient.reduceMotion) },
    ) {
        RegisterPage(navController)
    }
    composable<UserRoute.Setting> {
        SettingPage(navController)
    }
    // 以下三页的实现在本模块 src/full 源集（依赖 laundry-api / notice-api / payment-api
    // 等 full 专属契约），注册随之走本模块；lite 无这些页面，不存在同路由重复注册。
    composable<UserRoute.UpdateUserInfo> {
        UpdateUserInfoPage(navController)
    }
    composable<UserRoute.ApiConfig> {
        ApiConfigPage(navController)
    }
    composable<UserRoute.WalletTransactions> {
        WalletTransactionsPage(navController)
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
