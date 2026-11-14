package com.smartwash.ui.page.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.smartwash.BuildConfig
import com.smartwash.config.ServerConfig
import com.smartwash.R
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.di.GameBridgeEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接；供 WebViewClient 回调留痕） */
private const val TAG = "WebViewPage"

/**
 * 内置浏览器页（壳层留守页面，深链 web 分支与运营位 H5 承载）：
 * - 仅接受 http/https，否则展示错误态（EmptyState），杜绝 javascript: 等伪协议进 WebView；
 * - 原生 android.webkit.WebView + AndroidView 包裹（零新依赖），JavaScript 与 DOM 存储开启；
 * - 系统返回优先走网页历史（canGoBack 回退），历史为空才交还导航出栈；
 * - 加载进度为真实 ChromeClient 回调驱动的清氧细进度条（非伪造动画，无自主动效故无需
 *   reduced motion 降级）；
 * - 标题取路由参数，缺省「详情页」；
 * - chrome 模式（[GameChrome]）供游戏 H5 经桥/URL 参数收起原生标题栏与系统状态栏，
 *   初值取路由参数 `chrome`（避免先闪一帧原生栏），运行期由桥调用切换。
 *
 * 路由：[com.smartwash.common.ui.navigation.ShellRoute.WebView]，注册形态
 * "WebView?url={url}&title={title}&chrome={chrome}"，url/title 由 NavType 自动 URL 编码、
 * Navigation 解析后自动解码回原文。
 */
@Composable
fun WebViewPage(
    navController: NavHostController,
    url: String,
    title: String,
    chrome: String = GameChrome.FULL,
) {
    // url 白名单：仅 http/https 放行进 WebView（错误态兜底，不渲染 Web 容器）
    val isHttpUrl = url.startsWith("http://") || url.startsWith("https://")

    // 原生 chrome 模式：初值取路由参数（加载前即定，无闪帧），运行期由游戏桥切换
    var chromeMode by remember { mutableStateOf(GameChrome.normalize(chrome)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        // chrome=none 时整条标题返回栏收起（游戏自绘 chrome）；immersive 保留返回入口
        if (GameChrome.shouldShowHeader(chromeMode)) {
            PageHeader(
                title = title.ifBlank { stringResource(R.string.webview_default_title) },
                onBack = { navController.navigateUp() },
            )
        }
        if (isHttpUrl) {
            WebViewContent(
                url = url,
                chromeMode = chromeMode,
                onChromeChange = { chromeMode = it },
<<<<<<< HEAD
=======
                whitelistHosts = whitelistHosts,
                // A1 首屏闪烁修复：getTheme 首次取值即具体主题（不再经 'system' 让 H5 等推送），
            // 深色用户开游戏不再「先浅色后变深」；uiMode 变化仍靠 SmartWashWebView 推送兜底
            bridgeThemeMode = GameThemeBridgePolicy.resolveBridgeThemeMode(
                inAppThemeMode = GameThemeBridgePolicy.resolveCurrentTheme(
                    LocalConfiguration.current.uiMode,
                ),
            ),
                gameBridgeApi = gameBridgeApi,
>>>>>>> 5250503a (fix(Android): 修复游戏深色首屏闪烁与游戏中心空描述占位)
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            )
        } else {
            // 非法链接错误态：说明发生了什么（规范 §3.6）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    // EmptyState 的 icon 为占位参数不渲染（组件只出滚筒圆母题），传内置图标即可
                    icon = Icons.Default.Link,
                    message = stringResource(R.string.webview_error_url_unsupported)
                )
            }
        }
    }
}

/**
 * WebView 容器：细进度条 + 网页视图 + 加载失败错误态。
 * WebView 实例按导航条目生命周期创建/销毁（DisposableEffect onDispose destroy），
 * url 随路由条目固定，无需响应变化重建。
 *
 * 游戏 H5 主题桥（SmartWashGameBridge）挂载于本容器：
 * - 白名单 host（后端 API + MinIO endpoint，均取 BuildConfig 注入值）内 getTheme/setChrome 生效；
 * - 主题/uiMode 变化与 onPageFinished 双保险推送 window.__applySmartWashTheme(mode)；
 * - API 33+ 显式关闭算法加深（主题由 H5 自渲染，禁系统二次反色）；
 * - chrome 模式（标题栏/状态栏显隐）经 [GameChrome.apply] 作用于窗口，离开页面时复原，
 *   避免把沉浸式状态带进后续页面。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContent(
    url: String,
    chromeMode: String,
    onChromeChange: (String) -> Unit,
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

    // 主题桥白名单 host：后端 API + MinIO endpoint。
    // 2026-11-01：MinIO endpoint 改取运行期配置（ServerConfig，启动经 /web/config 拉取），
    // 不再用编译期注入的 BuildConfig.MINIO_ENDPOINT——域名变更无需发版。
    // 配置未就绪时（启动极早期）只含 API host，属可接受降级：游戏页由用户操作打开，此时配置早已就绪。
    val themeBridgeWhitelist = remember {
        GameThemeBridgePolicy.whitelistHostsOf(BuildConfig.BASE_URL, ServerConfig.minioEndpoint)
    }
    // getTheme 返回的主题模式：仓库无应用内主题开关 → 'system'（见 resolveBridgeThemeMode 注释）
    val bridgeThemeMode = remember { GameThemeBridgePolicy.resolveBridgeThemeMode(inAppThemeMode = null) }
    // 桥回调经 rememberUpdatedState 取值：remember{桥} 只建一次，回调需始终读到最新组合
    // （否则桥持有一份陈旧 lambda，chrome 切换会写不进当前状态）
    val currentOnChromeChange by rememberUpdatedState(onChromeChange)
    // 桥代理 API（存档/成绩/关卡）：经 EntryPoint 取 Hilt 供给的 Retrofit 实例；
    // 取用失败（极早期）按无 API 降级——数据方法回调失败，H5 回落 localStorage。
    val gameBridgeApi = remember {
        runCatching {
            EntryPointAccessors.fromActivity(
                context as Activity,
                GameBridgeEntryPoint::class.java,
            ).gameBridgeApi()
        }.getOrNull()
    }
    val themeBridge = remember {
        SmartWashGameBridge(
            whitelist = themeBridgeWhitelist,
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
                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
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
                        whitelist = themeBridgeWhitelist,
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
    // 否则沉浸式（隐藏状态栏）会跟着导航带进下一个页面（如游戏中心返回后的工作台）
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
            whitelist = themeBridgeWhitelist,
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
