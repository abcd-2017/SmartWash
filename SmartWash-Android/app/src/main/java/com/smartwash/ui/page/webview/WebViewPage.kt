package com.smartwash.ui.page.webview

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.smartwash.BuildConfig
import com.smartwash.R
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.config.ServerConfig
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.webview.GameChrome
import com.smartwash.common.ui.webview.GameThemeBridgePolicy
import com.smartwash.common.ui.webview.SmartWashWebView
import com.smartwash.di.GameBridgeEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
fun WebViewPage(
    navController: NavHostController,
    url: String,
    title: String,
    chrome: String = GameChrome.FULL,
) {
    val isHttpUrl = url.startsWith("http://") || url.startsWith("https://")
    var chromeMode by remember { mutableStateOf(GameChrome.normalize(chrome)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (GameChrome.shouldShowHeader(chromeMode)) {
            PageHeader(
                title = title.ifBlank { stringResource(R.string.webview_default_title) },
                onBack = { navController.navigateUp() },
            )
        }
        if (isHttpUrl) {
            val whitelistHosts = remember {
                GameThemeBridgePolicy.whitelistHostsOf(
                    ServerConfig.apiBaseUrl ?: BuildConfig.BASE_URL,
                    ServerConfig.minioEndpoint,
                )
            }
            val context = LocalContext.current
            val gameBridgeApi = remember {
                runCatching {
                    EntryPointAccessors.fromActivity(
                        context as Activity,
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Link,
                    message = stringResource(R.string.webview_error_url_unsupported)
                )
            }
        }
    }
}
