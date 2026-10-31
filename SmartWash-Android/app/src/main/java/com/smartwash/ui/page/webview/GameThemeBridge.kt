package com.smartwash.ui.page.webview

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.net.URI

/** 主题桥白名单与模式解析的纯函数集（单测锚定 GameThemeBridgePolicyTest） */
internal object GameThemeBridgePolicy {

    /**
     * 从 URL 提取 host（归一化：小写、去尾部 FQDN 点）。
     * 仅 http/https 放行（与 WebViewPage 的 scheme 白名单同语义）；解析失败/无 host 返回 null
     * （fail-closed：拿不到 host 一律视为非白名单来源）。
     */
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        return runCatching { URI(url).host }
            .getOrNull()
            ?.lowercase()
            ?.trimEnd('.')
            ?.takeIf { it.isNotEmpty() }
    }

    /** host 白名单判定（host 归一化后与白名单比对；白名单经 [whitelistHostsOf] 产出已归一化） */
    fun isAllowedHost(host: String?, whitelist: Set<String>): Boolean {
        val normalized = host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return false
        if (whitelist.isEmpty()) return false
        return whitelist.any { it.equals(normalized, ignoreCase = true) }
    }

    /** 由配置 URL 集合产出白名单 host 集（非法/空 URL 自动剔除） */
    fun whitelistHostsOf(vararg urls: String?): Set<String> = urls.mapNotNull(::hostOf).toSet()

    /**
     * getTheme 返回的主题模式（H5 侧取值 light/dark/system，空串=非白名单来源）：
     * App 无应用内主题开关（主题全量跟随系统，见 common/ui/theme/Theme.kt 的
     * isSystemInDarkTheme 判定），返回 'system' 由 H5 自有 matchMedia 跟随系统——
     * 与 App 主题判定同源，语义恒一致；未来若引入应用内主题开关，在此接入配置源。
     */
    fun resolveBridgeThemeMode(inAppThemeMode: String?): String = when (inAppThemeMode?.lowercase()) {
        GameThemeMode.LIGHT, GameThemeMode.DARK -> inAppThemeMode.lowercase()
        else -> GameThemeMode.SYSTEM
    }

    /** uiMode → 当前生效主题（解析后的具体值，供主题变化/页面加载完成时推送） */
    fun resolveCurrentTheme(uiMode: Int): String =
        if ((uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) GameThemeMode.DARK else GameThemeMode.LIGHT
}

/** 深浅色模式 key（H5 协议取值，见 GAME-THEME-PROMPT「深色模式同步机制」） */
object GameThemeMode {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"
}

private const val TAG = "SmartWashGameBridge"

/**
 * 游戏 H5 深色主题桥（注入对象名固定为 [GAME_THEME_BRIDGE_NAME]，H5 侧按此名取值）。
 *
 * WebViewPage 是通用容器（smartwash://web?url= 可装任意页），桥仅对白名单 host 生效：
 * - getTheme()：白名单内返回 App 当前主题模式（无应用内主题开关 → 'system'）；
 *   白名单外返回空串并记录（H5 端空串走 matchMedia 跟随系统，不报错）。
 * - 主题变化/页面加载完成的主动推送由 [pushGameTheme] 负责，推送前同样过 host 白名单。
 *
 * getTheme 由 WebView 的 JS 桥线程调用（非 UI 线程），因此不直接读 WebView：
 * 当前页 host 由 UI 线程在构造与 onPageStarted 刷新 [currentPageHost] 快照
 * （onPageStarted 先于页面任何 JS 执行，覆盖首帧与站内跳转；pushState 不换源无需处理）。
 */
internal class SmartWashGameBridge(
    private val whitelist: Set<String>,
    private val themeModeProvider: () -> String,
    private val onHostRejected: (String?) -> Unit = { Log.w(TAG, "getTheme 拒绝非白名单来源: $it") },
) {

    /** 当前页 host 快照（JS 桥线程读、UI 线程写） */
    @Volatile
    internal var currentPageHost: String? = null

    @JavascriptInterface
    fun getTheme(): String {
        val host = currentPageHost
        if (!GameThemeBridgePolicy.isAllowedHost(host, whitelist)) {
            onHostRejected(host)
            return ""
        }
        return themeModeProvider()
    }
}

/** JS 桥注入对象名（H5 侧 window.SmartWashGameBridge，协议固定） */
const val GAME_THEME_BRIDGE_NAME = "SmartWashGameBridge"

/** 主题推送脚本（H5 未定义钩子时为空操作，无报错） */
internal const val GAME_THEME_PUSH_SCRIPT = "window.__applySmartWashTheme&&__applySmartWashTheme('%s')"

/**
 * 主题变化 / onPageFinished 双保险推送（UI 线程调用）。
 * 白名单外静默跳过并记录——通用容器不得向第三方 H5 泄露 App 主题信号。
 */
internal fun pushGameTheme(webView: WebView, mode: String, whitelist: Set<String>) {
    val host = GameThemeBridgePolicy.hostOf(webView.url)
    if (!GameThemeBridgePolicy.isAllowedHost(host, whitelist)) {
        Log.w(TAG, "主题推送跳过非白名单来源: $host")
        return
    }
    webView.evaluateJavascript(GAME_THEME_PUSH_SCRIPT.format(mode), null)
}
