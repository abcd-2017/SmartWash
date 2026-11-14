package com.smartwash.common.ui.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.smartwash.common.ui.R
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.AppLogger
import com.smartwash.network.api.GameBridgeApi

/** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接；供 WebViewClient 回调留痕） */
private const val TAG = "SmartWashWebView"

/**
 * 游戏桥 API 提供者（common 侧普通接口；app 壳以 Hilt @EntryPoint 继承实现，
 * 见 di/GameBridgeModule GameBridgeEntryPoint）。
 *
 * 2026-11-14 架构修正：公共 H5 容器（app 壳 WebViewPage）保持纯净，游戏简介等
 * 游戏域业务由 feature:game 自建承载页承担；两者共用本 webview 能力包
 * （桥/主题/chrome/白名单/进度条/错误态），避免重复实现。
 */
interface GameBridgeApiProvider {
    fun gameBridgeApi(): GameBridgeApi?
}

/**
 * 公共游戏 H5 容器（游戏主题桥 + 存档/成绩/关卡数据桥 + chrome 控制 + 进度条/错误态）。
 *
 * 使用方：
 * - app 壳 [com.smartwash.ui.page.webview.WebViewPage]（深链 smartwash://web?url= 与运营位 H5）；
 * - feature:game 游戏承载页（游戏中心进入，带简介入口）。
 *
 * 桥仅对白名单 host 生效（fail-closed）；白名单由调用方按自身数据源组装传入——
 * 公共容器不内置任何业务字段，业务数据（如游戏简介）由各域承载页自理。
 *
 * @param url 可玩地址（http/https；非法值由调用方拦截，本组件只处理合法 URL）
 * @param chromeMode 原生 chrome 模式初值（[GameChrome] 协议：full/immersive/none）
 * @param onChromeChange 桥运行期切换 chrome 的回调（UI 线程）
 * @param whitelistHosts 桥生效的 host 白名单（已归一化）
 * @param bridgeThemeMode 桥 getTheme 返回值（'system' 与 App 主题判定同源）
 * @param gameBridgeApi 数据桥 API（存档/成绩/关卡）；null 时数据方法回调失败，H5 降级 localStorage
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SmartWashWebView(
    url: String,
    chromeMode: String,
    onChromeChange: (String) -> Unit,
    whitelistHosts: Set<String>,
    bridgeThemeMode: String,
    gameBridgeApi: GameBridgeApi?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    // uiMode 变化经 LocalConfiguration 观察触发重推（Activity 因 uiMode 重建时由
    // 重建后的 onPageFinished 兜底，两种路径覆盖）
    val configuration = LocalConfiguration.current
    // 0-100 真实加载进度（WebChromeClient 回调驱动；1-99 显示细进度条）
    var progress by remember { mutableIntStateOf(0) }
    // 主框架加载失败标记（仅主框架错误置位，子资源失败不打断页面）
    var loadError by remember { mutableStateOf(false) }
    // 网页历史回退可用性（onPageFinished 后刷新）
    var canGoBack by remember { mutableStateOf(false) }

    // 桥回调经 rememberUpdatedState 取值：remember{桥} 只建一次，回调需始终读到最新组合
    val currentOnChromeChange by rememberUpdatedState(onChromeChange)
    val themeBridge = remember {
        SmartWashGameBridge(
            whitelist = whitelistHosts,
            themeModeProvider = { bridgeThemeMode },
            onChromeChange = { currentOnChromeChange(GameChrome.normalize(it)) },
            gameBridgeApi = gameBridgeApi,
        ).apply {
            currentPageHost = GameThemeBridgePolicy.hostOf(url)
        }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            // 常规 H5（含登录态 localStorage）依赖 DOM 存储，关闭会导致大量页面异常
            settings.domStorageEnabled = true
            // 游戏 H5 主题由桥同步、H5 自行渲染，禁止系统算法加深二次反色（API 33+ 显式关；
            // targetSdk 35 下 API 29-32 的 force dark AUTO 本就不生效，且全仓未调用 setForceDark）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                settings.isAlgorithmicDarkeningAllowed = false
            }
            addJavascriptInterface(themeBridge, GAME_THEME_BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    // http/https 留在应用内 WebView；其余 scheme（tel/mailto 等）交系统处理
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    return runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                    }.isSuccess
                }

                override fun onPageStarted(view: WebView, startedUrl: String?, favicon: Bitmap?) {
                    // 新一轮导航开始时复位错误态与进度
                    loadError = false
                    progress = 0
                    // 刷新主题桥的当前页 host 快照（onPageStarted 先于页面 JS，覆盖首帧与站内跳转）
                    themeBridge.currentPageHost = GameThemeBridgePolicy.hostOf(startedUrl)
                }

                override fun onPageFinished(view: WebView, finishedUrl: String?) {
                    canGoBack = view.canGoBack()
                    // 双保险推送：覆盖页面加载晚于 uiMode 变化推送的时序
                    pushGameTheme(
                        webView = view,
                        mode = GameThemeBridgePolicy.resolveCurrentTheme(view.resources.configuration.uiMode),
                        whitelist = whitelistHosts,
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    // 仅主框架失败才进错误态（子资源 404/超时不应打断整页）
                    if (request.isForMainFrame) {
                        // 外部加载失败（WebViewClient 运行期回调，非组合期渲染路径）；
                        // 只记错误码与描述，不记完整 URL（深链/运营链接 query 不可控）
                        AppLogger.error(
                            TAG,
                            "WebView 主框架加载失败：code=${error.errorCode} desc=${error.description}"
                        )
                        loadError = true
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    // API < 23 回调兜底：与当前主框架 URL 一致才置错误态
                    if (failingUrl == view.url) loadError = true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            loadUrl(url)
        }
    }

    // 注入 WebView 提供者（桥异步结果回调用；SideEffect 避免组合期写状态）
    SideEffect { themeBridge.webViewProvider = { webView } }

    // 系统返回优先回退网页历史（返回键语义跟随用户浏览预期），历史为空才出栈本页
    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    // 导航条目离开时释放 WebView（销毁内部线程与 WebCore，防内存泄漏）
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    // chrome 模式生效/复原：进入页面按当前模式设置窗口，离开时一律复原为 FULL——
    // 否则沉浸式（隐藏状态栏）会跟着导航带进下一个页面
    DisposableEffect(chromeMode) {
        GameChrome.apply(context, view, chromeMode)
        onDispose { GameChrome.apply(context, view, GameChrome.FULL) }
    }

    // 主题/uiMode 变化主动推送（H5 侧 window.__applySmartWashTheme 钩子）：
    // 首次组合亦推一次（页面未加载时为空操作），后续 uiMode 变化经 LocalConfiguration
    // 观察触发；推送前统一过 host 白名单（pushGameTheme 内置），白名单外静默跳过。
    LaunchedEffect(configuration.uiMode) {
        pushGameTheme(
            webView = webView,
            mode = GameThemeBridgePolicy.resolveCurrentTheme(configuration.uiMode),
            whitelist = whitelistHosts,
        )
    }

    Box(modifier = modifier) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        // 清氧细进度条：真实进度驱动（progress 隐藏 0 与 100 两端，避免空载/完成残留）
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter),
                color = AppColors.colorScheme.primary,
                trackColor = AppColors.colorScheme.background,
                strokeCap = StrokeCap.Square,
            )
        }

        // 加载失败错误态：说明发生了什么 + 给重试（规范 §3.6）
        if (loadError) {
            EmptyState(
                // 占位参数不渲染（组件只出滚筒圆母题）
                icon = Icons.Default.Link,
                message = stringResource(R.string.webview_error_load),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(AppColors.colorScheme.background),
                action = {
                    AppButton(
                        small = true,
                        text = stringResource(R.string.webview_action_retry),
                        onClick = {
                            loadError = false
                            progress = 0
                            webView.reload()
                        },
                        variant = AppButtonVariant.TEXT,
                    )
                }
            )
        }
    }
}
