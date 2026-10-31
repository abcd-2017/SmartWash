package com.smartwash.ui.page.webview

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 游戏 H5 深色主题桥 JVM 单测（锚点）：
 * - host 白名单判定纯函数（fail-closed：解析失败/无 host/空白名单一律拒绝）；
 * - 白名单外来源 getTheme 返回空串（WebViewPage 通用容器，桥不对第三方 H5 生效）；
 * - 主题模式解析（getTheme 模式 / 推送解析值）与推送脚本锚点。
 */
class GameThemeBridgePolicyTest {

    // ---------- hostOf：URL → 归一化 host ----------

    @Test
    fun `hostOf 提取 http https 的 host 并归一化`() {
        assertEquals("8.148.70.81", GameThemeBridgePolicy.hostOf("http://8.148.70.81:9000/"))
        assertEquals(
            "minio.example.com",
            GameThemeBridgePolicy.hostOf("https://MinIO.Example.COM/smartwash/game/snake/index.html"),
        )
        // 尾部 FQDN 点归一化掉，与无点 host 视为同一来源
        assertEquals("example.com", GameThemeBridgePolicy.hostOf("https://example.com./a/b"))
        // host 仅取主机名，不含端口与路径
        assertEquals(
            "192.168.1.61",
            GameThemeBridgePolicy.hostOf("http://192.168.1.61:9000/smartwash/game/g2048/index.html"),
        )
    }

    @Test
    fun `hostOf 仅放行 http https 其余一律 null`() {
        // 与 WebViewPage 的 scheme 白名单同语义：javascript: 等伪协议不得进入判定
        assertEquals(null, GameThemeBridgePolicy.hostOf("javascript:alert(1)"))
        assertEquals(null, GameThemeBridgePolicy.hostOf("file:///sdcard/1.html"))
        assertEquals(null, GameThemeBridgePolicy.hostOf("about:blank"))
        assertEquals(null, GameThemeBridgePolicy.hostOf("ftp://example.com/a"))
        assertEquals(null, GameThemeBridgePolicy.hostOf("smartwash://game-center"))
    }

    @Test
    fun `hostOf 非法与空输入 fail-closed 返回 null`() {
        assertEquals(null, GameThemeBridgePolicy.hostOf(null))
        assertEquals(null, GameThemeBridgePolicy.hostOf(""))
        assertEquals(null, GameThemeBridgePolicy.hostOf("   "))
        assertEquals(null, GameThemeBridgePolicy.hostOf("http://"))
        assertEquals(null, GameThemeBridgePolicy.hostOf("http://exa mple.com/x"))
    }

    // ---------- isAllowedHost：白名单判定 ----------

    @Test
    fun `白名单内 host 放行且大小写不敏感`() {
        val whitelist = setOf("8.148.70.81", "minio.example.com")
        assertTrue(GameThemeBridgePolicy.isAllowedHost("8.148.70.81", whitelist))
        assertTrue(GameThemeBridgePolicy.isAllowedHost("minio.example.com", whitelist))
        assertTrue(GameThemeBridgePolicy.isAllowedHost("MINIO.EXAMPLE.com", whitelist))
        assertTrue(GameThemeBridgePolicy.isAllowedHost("minio.example.com.", whitelist))
    }

    @Test
    fun `白名单外或非法 host 一律拒绝（fail-closed 锚点）`() {
        val whitelist = setOf("8.148.70.81", "minio.example.com")
        assertFalse(GameThemeBridgePolicy.isAllowedHost("evil.example.com", whitelist))
        assertFalse(GameThemeBridgePolicy.isAllowedHost("sub.minio.example.com", whitelist))
        // 白名单 host 的前缀/后缀拼接不构成放行
        assertFalse(GameThemeBridgePolicy.isAllowedHost("8.148.70.81.evil.com", whitelist))
        assertFalse(GameThemeBridgePolicy.isAllowedHost(null, whitelist))
        assertFalse(GameThemeBridgePolicy.isAllowedHost("", whitelist))
        // 空白名单拒绝一切来源
        assertFalse(GameThemeBridgePolicy.isAllowedHost("8.148.70.81", emptySet()))
    }

    // ---------- whitelistHostsOf：配置 URL → 白名单集 ----------

    @Test
    fun `白名单由配置 URL 产出 host 集并去重`() {
        // 与 BuildConfig 注入值同构：BASE_URL / MINIO_ENDPOINT 各取 host（端口不参与判定）
        val whitelist = GameThemeBridgePolicy.whitelistHostsOf(
            "http://8.148.70.81:9000/",
            "http://192.168.1.61:9000",
        )
        assertEquals(setOf("8.148.70.81", "192.168.1.61"), whitelist)
        // 非法/空配置自动剔除，不产生放行面
        assertEquals(setOf("api.example.com"), GameThemeBridgePolicy.whitelistHostsOf("https://api.example.com/", "", null))
        assertEquals(emptySet<String>(), GameThemeBridgePolicy.whitelistHostsOf(null, "about:blank"))
    }

    // ---------- 桥对象：白名单外 getTheme 返回空串（验收锚点） ----------

    @Test
    fun `getTheme 白名单内返回主题模式`() {
        val bridge = SmartWashGameBridge(setOf("8.148.70.81"), { "system" })
        bridge.currentPageHost = "8.148.70.81"
        assertEquals("system", bridge.getTheme())
    }

    @Test
    fun `getTheme 白名单外来源返回空串并记录来源（验收锚点）`() {
        val rejected = mutableListOf<String?>()
        val bridge = SmartWashGameBridge(
            whitelist = setOf("8.148.70.81", "minio.example.com"),
            themeModeProvider = { "dark" },
            onHostRejected = { rejected.add(it) },
        )

        // 第三方页面（smartwash://web?url= 可装任意页）
        bridge.currentPageHost = "evil.example.com"
        assertEquals("", bridge.getTheme())
        // 无 host 快照（异常时序兜底，fail-closed）
        bridge.currentPageHost = null
        assertEquals("", bridge.getTheme())

        assertEquals(listOf("evil.example.com", null), rejected)
    }

    // ---------- 主题模式解析 ----------

    @Test
    fun `getTheme 模式解析有应用内开关取开关值其余跟随系统`() {
        assertEquals(GameThemeMode.LIGHT, GameThemeBridgePolicy.resolveBridgeThemeMode("light"))
        assertEquals(GameThemeMode.DARK, GameThemeBridgePolicy.resolveBridgeThemeMode("DARK"))
        // 仓库当前无应用内主题开关（null 入口）→ 'system'，H5 走自有 matchMedia 跟随系统
        assertEquals(GameThemeMode.SYSTEM, GameThemeBridgePolicy.resolveBridgeThemeMode(null))
        assertEquals(GameThemeMode.SYSTEM, GameThemeBridgePolicy.resolveBridgeThemeMode(""))
        assertEquals(GameThemeMode.SYSTEM, GameThemeBridgePolicy.resolveBridgeThemeMode("unknown"))
    }

    @Test
    fun `推送模式解析 uiMode 夜间掩码`() {
        assertEquals(GameThemeMode.DARK, GameThemeBridgePolicy.resolveCurrentTheme(Configuration.UI_MODE_NIGHT_YES))
        assertEquals(GameThemeMode.LIGHT, GameThemeBridgePolicy.resolveCurrentTheme(Configuration.UI_MODE_NIGHT_NO))
        assertEquals(GameThemeMode.LIGHT, GameThemeBridgePolicy.resolveCurrentTheme(0))
    }

    // ---------- 推送脚本锚点 ----------

    @Test
    fun `推送脚本与 H5 协议钩子逐字对齐`() {
        assertEquals(
            "window.__applySmartWashTheme&&__applySmartWashTheme('dark')",
            GAME_THEME_PUSH_SCRIPT.format(GameThemeMode.DARK),
        )
        assertEquals(
            "window.__applySmartWashTheme&&__applySmartWashTheme('light')",
            GAME_THEME_PUSH_SCRIPT.format(GameThemeMode.LIGHT),
        )
    }

    @Test
    fun `桥对象名与 H5 协议固定命名一致`() {
        assertEquals("SmartWashGameBridge", GAME_THEME_BRIDGE_NAME)
    }
}
