@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.smartwash.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.smartwash.BuildConfig
import com.smartwash.R
import com.smartwash.common.ui.navigation.LocalSharedTransitionScope
import com.smartwash.common.ui.navigation.NavGraphAmbient
import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppBrand
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.LiteAuthGradientBottom
import com.smartwash.common.ui.theme.LiteAuthGradientTop
import com.smartwash.common.ui.theme.SmartWashAndroidTheme
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.deeplink.DeepLinkAction
import com.smartwash.common.utils.isReduceMotionEnabled
import com.smartwash.common.utils.performHaptic
import com.smartwash.deeplink.DeepLinkHandler
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.api.model.LoginEvent
import com.smartwash.feature.user.api.model.LogoutReason
import com.smartwash.feature.user.impl.ui.LocalCheckUpdateContent
import com.smartwash.ui.navigation.shellEnterTransition
import com.smartwash.ui.navigation.shellExitTransition
import com.smartwash.ui.page.update.UpdateFlowHost
import com.smartwash.ui.page.update.rememberCheckUpdateContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * 单 Activity 壳（T8.1 拆路由后）：NavHost 只做聚合——壳层留守页面与各 feature 域
 * 经 [NavGraphContributor] 多绑定自注册（各域 impl 以 `@IntoSet` 贡献 `xxxGraph`，
 * 路由常量单一事实来源在各域 api 模块）；壳层另持更新流程（变体入口 [UpdateFlowHost]，
 * full 组装热更新链路 / lite 空实现）、深链分发（[DeepLinkHandler]，host 由各域贡献者注册、
 * 壳层自持注册表，Manifest 声明 scheme 由变体 manifestPlaceholders 注入）
 * 与 401/登录态事件收集三项壳职责。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 用户域经 UserApi 契约消费（T5.3）：会话事件收集 + 登录态判断，不触碰 impl 类型
    @Inject lateinit var userApi: UserApi
    @Inject lateinit var workManager: WorkManager
    @Inject lateinit var deepLinkHandler: DeepLinkHandler

    /**
     * 各域导航图贡献者（Hilt @IntoSet 多绑定，common:ui 的 [NavGraphContributor] 契约）。
     * 替代原先壳层硬编码的静态 import + 逐个 `xxxGraph(navController)` 调用。
     */
    @Inject
    lateinit var navGraphContributors: Set<@JvmSuppressWildcards NavGraphContributor>

    /**
     * 待处理深链 URI（VIEW intent 的 data）。冷启动（onCreate）与热启动（onNewIntent，
     * singleTask 复用实例）都只写这个 Compose state，由 setContent 内的收集端统一
     * 「resolve → navigate」，保证消费时机在 NavHost 图就绪之后。
     */
    private var pendingDeepLinkUri by mutableStateOf<String?>(null)

    /**
     * 启动时申请通知权限（Android 13+）：APK 下载进度需要在通知栏展示，
     * 未授权则通知不显示，但下载功能仍可用。
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 无论授权与否都不阻塞用户，仅影响通知栏是否展示下载进度
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 冷启动深链：VIEW intent 携带 smartwash:// data 时记录到 state，
        // 由 setContent 内收集端在 NavHost 就绪后统一 resolve + navigate
        pendingDeepLinkUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data?.toString()?.takeIf { it.isNotBlank() }

        // 启动时主动申请通知权限（Android 13+ 需要运行时授权）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val view = LocalView.current

            // 登录页统一跳转：replace 语义（清空到首页根再压登录页）+ 去重（已在登录页则不动）。
            // 供启动守卫、受保护深链守卫与 NEED_LOGIN / UNAUTHORIZED 会话事件共用，
            // 避免多处 push 登录页造成堆叠或在登录页上重复压栈。
            val goLogin = {
                if (navController.currentBackStackEntry?.destination
                        ?.hasRoute<UserRoute.Login>() != true
                ) {
                    navController.navigate(UserRoute.Login) {
                        popUpTo<ShellRoute.Home> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            // 设置页「检查更新」插槽（变体入口）：full 注入更新区块，lite 为 null（隐藏区块）。
            // 符号在 app/src/full 与 app/src/lite 各有一份同签名实现，main 只按契约调用。
            val checkUpdateContent = rememberCheckUpdateContent()
            // reduced motion 随导航刷新 — 既避免在组合期反复做 ContentResolver 查询，
            // 又能让用户在系统设置里改完「移除动画」切回应用后立即生效。
            val backStackEntry by navController.currentBackStackEntryAsState()
            val reduceMotion = remember(backStackEntry) { isReduceMotionEnabled(context) }

            // 深链统一消费端：冷启动（onCreate）与热启动（onNewIntent）都写
            // [pendingDeepLinkUri]，这里在 NavHost 图组合完成后 resolve + navigate。
            // 登录守卫（P-2001）：未登录不放行受保护深链——公开深链（WebView / 认证页自身 /
            // 纯本地页）与 Unknown（仅 Toast 不导航）直接分发；受保护 host 未登录时落登录页
            // （对齐 401 / 未登录拦截语义的落点），已登录才执行目标导航。
            // 会话判断用挂起读 [UserApi.hasSession]：冷启动深链可能早于 SessionInitTask
            // 预热完成，isLogin() 的内存同步读存在误报未登录的竞态窗口。
            LaunchedEffect(pendingDeepLinkUri) {
                val uri = pendingDeepLinkUri ?: return@LaunchedEffect
                pendingDeepLinkUri = null // 先消费再处理，防止异常/重组导致重复导航
                try {
                    val action = deepLinkHandler.resolve(uri)
                    if (!deepLinkHandler.isPublicAction(action, uri) && !userApi.hasSession()) {
                        // 未登录 + 受保护深链：拦截并落登录页（低频动作；LaunchedEffect 副作用分支留痕上限 debug）
                        AppLogger.debug(TAG, "深链登录守卫拦截：uri=$uri（未登录 + 受保护 host）")
                        Toast.makeText(
                            context,
                            context.getString(R.string.please_login),
                            Toast.LENGTH_SHORT
                        ).show()
                        // replace 语义落登录页（goLogin 内已去重 + 清空到首页根）
                        goLogin()
                        return@LaunchedEffect
                    }
                    deepLinkHandler.navigate(navController, action)
                } catch (e: CancellationException) {
                    // 协程取消原样 rethrow（组件销毁等），不能吞成「不支持」
                    throw e
                } catch (e: Exception) {
                    // resolve 内部已静默字典失败，此处兜意外异常，按不支持链接提示
                    // （LaunchedEffect 一次性副作用分支，留痕上限 debug）
                    AppLogger.debug(TAG, "深链消费意外异常，降级不支持提示：uri=$uri", e)
                    deepLinkHandler.navigate(navController, DeepLinkAction.Unknown(uri))
                }
            }

            // 启动登录守卫：首页恒为第一页（startDestination=Home），仅未登录才拦截落登录页。
            // 已登录冷启动不再「先闪登录页 → 登录页自动跳首页」；未登录时首页首帧后即替换为登录页。
            LaunchedEffect(Unit) {
                if (!userApi.hasSession()) goLogin()
            }

            // 收集用户域登录态事件（UserApi.loginEvents，T5.3 自 SessionEventBus 切换）：
            // 未登录拦截 / 401 登录失效 → 统一跳登录页，reason 保留文案与触感差异。
            // 事件源（user-impl 内 SessionEventBus）已去重 + navigate 加 launchSingleTop，
            // 避免连发 401 堆叠多个登录页；token 清理由拦截器幂等处理，这里不再重复清。
            LaunchedEffect(userApi) {
                userApi.loginEvents.collect { event ->
                    when (event) {
                        is LoginEvent.LoggedOut -> when (event.reason) {
                            LogoutReason.NEED_LOGIN -> {
                                // 未登录拦截跳登录（低频会话事件；LaunchedEffect 副作用分支留痕上限 debug）
                                AppLogger.debug(TAG, "会话事件 NEED_LOGIN：壳层跳转登录页")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.please_login),
                                    Toast.LENGTH_SHORT
                                ).show()
                                goLogin()
                            }

                            LogoutReason.UNAUTHORIZED -> {
                                // 401 登录失效跳转（低频会话事件；LaunchedEffect 副作用分支留痕上限 debug，
                                // release 侧留痕由 user-impl 事件源/拦截器承担）
                                AppLogger.debug(TAG, "会话事件 UNAUTHORIZED：壳层跳转登录页（401 去重后单次跳转）")
                                view.performHaptic(HapticEffect.ERROR)
                                delay(200)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.please_re_login),
                                    Toast.LENGTH_SHORT
                                ).show()
                                goLogin()
                            }

                            // 主动登出：设置页自理导航，壳层不响应
                            LogoutReason.LOGOUT -> {}
                        }

                        // 登录/注册成功：登录页自理导航，壳层不响应
                        LoginEvent.LoggedIn -> {}
                    }
                }
            }

            SmartWashAndroidTheme(brand = appBrand) {
                // 全应用动效降级开关（规范 7.8）：叶子节点经 motionSpec()/LocalReduceMotion 消费
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    // 更新流程（事件收集 → 下载调度 → 弹窗展示；T8.1 自本文件抽至壳层 UpdateFlow）：
                    // 必须在主题包装内组合——否则更新弹窗位于 SmartWashAndroidTheme 之外，
                    // MaterialTheme/AppColors 均回落浅色默认值，深色模式下弹窗仍显示浅色样式。
                    // 变体入口：full 组装真实链路；lite 为空实现（无更新域）。
                    UpdateFlowHost(context, workManager)

                    Surface(modifier = Modifier.fillMaxSize()) {
                        // 转场露底色跟随当前目的地：导航命令执行时 currentBackStackEntry 即更新，
                        // 动画在其上播放——认证页透认证渐变（修复登录↔注册白闪），
                        // 其余页面透主题底色，避免全局纯绿背景在非认证转场中透色。
                        val currentDestination = backStackEntry?.destination
                        val isAuthRoute = currentDestination?.hasRoute<UserRoute.Login>() == true ||
                            currentDestination?.hasRoute<UserRoute.Register>() == true
                        // 图注册/转场所需的组合期环境值快照（NavHost builder 非组合上下文，
                        // 各域图无法直接读 LocalReduceMotion；转场 lambda 调用时读最新值）。
                        // 写入放 SideEffect：避免组合期副作用，且晚于本次组合的图注册执行。
                        SideEffect {
                            NavGraphAmbient.reduceMotion = reduceMotion
                        }
                        // 共享元素转场容器（2026-11-01）：承载「短链 item → 详情页」一镜到底。
                        // 仅当进出两侧存在相同 key 的 sharedElement 时才激活，不影响其余页面转场。
                        SharedTransitionLayout {
                            // 共享元素转场 scope 下传各域图（当前仅工具箱短链 item→详情消费）
                            val sharedTransitionScope = this
                            CompositionLocalProvider(
                                LocalSharedTransitionScope provides sharedTransitionScope,
                                // 检查更新行插槽（变体入口注入；lite 为 null → 设置页隐藏更新区块）
                                LocalCheckUpdateContent provides checkUpdateContent,
                            ) {
                                NavHost(
                                    navController = navController,
                                    // 首页恒为第一页：已登录直接落首页；未登录由上方启动守卫 replace 到登录页
                                    startDestination = ShellRoute.Home,
                                    enterTransition = { shellEnterTransition(reduceMotion) },
                                    exitTransition = { shellExitTransition(reduceMotion) },
                                    popEnterTransition = { shellEnterTransition(reduceMotion) },
                                    popExitTransition = { shellExitTransition(reduceMotion) },
                                    modifier = Modifier.background(
                                        if (isAuthRoute) {
                                            // 认证渐变随品牌：full 清氧绿 / lite 暖橙
                                            Brush.verticalGradient(
                                                if (appBrand == AppBrand.TodayEat) {
                                                    listOf(LiteAuthGradientTop, LiteAuthGradientBottom)
                                                } else {
                                                    listOf(AuthGradientTop, AuthGradientBottom)
                                                }
                                            )
                                        } else {
                                            SolidColor(MaterialTheme.colorScheme.background)
                                        }
                                    )
                                ) {
                                    // 各域图经 Hilt @IntoSet 多绑定贡献，壳层不再硬编码静态调用；
                                    // 集合顺序不作路由语义承诺（路由之间无先后依赖）。
                                    for (contributor in navGraphContributors) {
                                        with(contributor) {
                                            register(navController)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 热启动深链（Manifest launchMode=singleTask 复用既有实例）：VIEW intent 进这里，
     * 写入 [pendingDeepLinkUri] 交给 setContent 内收集端消费。setIntent 同步最新
     * intent，防止后续重建（配置变更）时拿到过期深链重复跳转。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data?.toString()?.takeIf { it.isNotBlank() }
            ?.let { pendingDeepLinkUri = it }
    }

    /**
     * 当前变体品牌：由 flavor 写入的 [BuildConfig.APP_CHANNEL] 决定（full=SmartWash / lite=TodayEat）。
     * 主题层据此切换色板；不在 common 层硬编码品牌（common 不可见 app 的 BuildConfig）。
     */
    private val appBrand: AppBrand
        get() = if (BuildConfig.APP_CHANNEL == CHANNEL_LITE) AppBrand.TodayEat else AppBrand.SmartWash

    private companion object {
        const val TAG = "MainActivity"

        /** lite 变体渠道名（与 app/build.gradle 的 flavor `buildConfigField "APP_CHANNEL"` 同源） */
        const val CHANNEL_LITE = "lite"
    }
}
