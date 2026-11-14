package com.smartwash.common.ui.webview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 原生顶部 chrome 模式（游戏 H5 经桥 / URL 参数控制，见 GAME-THEME.md「chrome 控制」）。
 *
 * 游戏运行在 App 内 WebView 里，其自绘标题栏与原生 [com.smartwash.common.ui.components.PageHeader]
 * 叠成「两条标题栏」。H5 侧有了本对象，就能在加载时声明自己要哪一种 chrome：
 *
 * - [FULL]（默认）：原生标题返回栏 + 系统状态栏，与应用内其他页面一致；
 * - [IMMERSIVE]：保留标题返回栏（返回入口不能丢），隐藏系统状态栏，全屏画布游戏多得一行可视高度；
 * - [NONE]：全隐，由页面自绘 chrome（需自备返回入口）。
 *
 * 桥侧入口 [SmartWashGameBridge.setChrome]（白名单 host 内生效，非法值回落 [FULL]）；
 * URL 参数 `chrome` 供原生侧在加载前给定初值，避免先闪一帧原生栏再隐藏。
 */
object GameChrome {

    /** 原生标题返回栏 + 系统状态栏（默认，与应用其他页面一致） */
    const val FULL = "full"

    /** 保留标题返回栏，隐藏系统状态栏（全屏画布游戏用） */
    const val IMMERSIVE = "immersive"

    /** 全隐：标题返回栏与系统状态栏都隐藏（页面自绘 chrome） */
    const val NONE = "none"

    /** 合法取值集合（用于归一化判定） */
    val MODES: Set<String> = setOf(FULL, IMMERSIVE, NONE)

    /**
     * 归一化：非法/空值回落 [FULL]（fail-safe——脏值不应让页面丢掉返回入口或状态栏）。
     */
    fun normalize(mode: String?): String = mode?.takeIf { it in MODES } ?: FULL

    /** 是否显示原生标题返回栏 */
    fun shouldShowHeader(mode: String): Boolean = mode != NONE

    /** 是否显示系统状态栏 */
    fun shouldShowStatusBar(mode: String): Boolean = mode == FULL

    /**
     * 应用 chrome 到窗口。
     * 上下文非 Activity（如 ApplicationContext）时静默跳过——chrome 只可能发生在页面上，
     * 拿不到窗口不应抛异常打断页面。
     */
    fun apply(context: Context, view: View, mode: String) {
        val window = context.findActivity()?.window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        if (shouldShowStatusBar(mode)) {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            // 隐藏后允许上滑临时唤出（沉浸式常规行为），避免用户再也看不到状态栏
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** 从任意 Context 解包出 Activity（Compose 的 LocalContext 可能是 ContextWrapper 链） */
    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
