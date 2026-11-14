package com.smartwash.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.config.ServerConfig
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.webview.GameChrome
import com.smartwash.common.ui.webview.GameThemeBridgePolicy
import com.smartwash.common.ui.webview.SmartWashWebView
import com.smartwash.common.utils.pressable
import com.smartwash.feature.game.R
import com.smartwash.feature.game.di.GameBridgeEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 游戏域 H5 承载页（2026-11-14 架构修正后）：游戏中心进入的游戏页。
 *
 * 与公共容器（app 壳 WebViewPage）的区别：本页携带游戏业务数据——[desc] 玩法简介，
 * 页头右侧显示「简介」入口（AppInfoDialog 承载）。容器能力（桥/主题/chrome/进度/错误态）
 * 复用 common:ui 的 [SmartWashWebView]，公共容器保持零业务字段。
 *
 * @param url 可玩地址（后端下发，端上零拼装）
 * @param title 游戏名（页头标题）
 * @param desc 一句话玩法说明（服务端文案）；空串时不显示简介入口（不误导）
 */
@Composable
fun GameWebViewPage(
    navController: NavHostController,
    url: String,
    title: String,
    desc: String = "",
) {
    // 游戏桥 chrome 协议：全屏画布类游戏经桥切 immersive/none，卡片式保持 full
    var chromeMode by remember { mutableStateOf(GameChrome.FULL) }
    var showIntro by remember { mutableStateOf(false) }
    val headerTitle = title.ifBlank { stringResource(R.string.game_webview_default_title) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (GameChrome.shouldShowHeader(chromeMode)) {
            PageHeader(
                title = headerTitle,
                onBack = { navController.navigateUp() },
                actions = {
                    // 简介入口：仅在有服务端文案时出现（深链等无数据路径不误导）
                    if (desc.isNotBlank()) {
                        // §7 禁 Material 交互组件：自绘 48dp 按压热区（scale 0.97 反馈）
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pressable(onClick = { showIntro = true }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.game_webview_action_intro),
                                modifier = Modifier.size(24.dp),
                                tint = AppColors.colorScheme.textSecondary,
                            )
                        }
                    }
                },
            )
        }

        // 桥数据源：白名单 = API host + MinIO endpoint（common:ui ServerConfig，启动任务写入）；
        // API 经 feature 本地 @EntryPoint 取用（Hilt 全组件共享 app 的 GameBridgeApi 供给）
        val whitelistHosts = remember {
            GameThemeBridgePolicy.whitelistHostsOf(
                ServerConfig.apiBaseUrl,
                ServerConfig.minioEndpoint,
            )
        }
        val context = LocalContext.current
        val gameBridgeApi = remember {
            runCatching {
                EntryPointAccessors.fromActivity(
                    context as android.app.Activity,
                    GameBridgeEntryPoint::class.java,
                ).gameBridgeApi()
            }.getOrNull()
        }
        SmartWashWebView(
            url = url,
            chromeMode = chromeMode,
            onChromeChange = { chromeMode = it },
            whitelistHosts = whitelistHosts,
            // A1 首屏闪烁修复：getTheme 首次取值即具体主题（不再经 'system' 让 H5 等推送），
            // 深色用户开游戏不再「先浅色后变深」；uiMode 变化仍靠 SmartWashWebView 推送兜底
            bridgeThemeMode = GameThemeBridgePolicy.resolveBridgeThemeMode(
                inAppThemeMode = GameThemeBridgePolicy.resolveCurrentTheme(
                    LocalConfiguration.current.uiMode,
                ),
            ),
            gameBridgeApi = gameBridgeApi,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        )
    }

    // 游戏简介弹窗：清氧 AppInfoDialog 自带入场动效与 reduced motion 降级
    if (showIntro) {
        AppInfoDialog(
            title = headerTitle,
            message = desc,
            onDismiss = { showIntro = false },
        )
    }
}
