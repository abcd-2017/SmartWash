package com.smartwash.deeplink

import android.net.Uri
import android.widget.Toast
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.deeplink.DeepLinkAction
import com.smartwash.common.utils.deeplink.DeepLinkParser
import com.smartwash.common.utils.navigation.SmartWashRoute
import com.smartwash.network.api.DeeplinkRouteDict
import com.smartwash.ui.page.webview.GameChrome
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 深链处理器（壳层导航聚合点自持，@Singleton 经 Hilt 注入）：
 * - [resolve]：解析深链为 [DeepLinkAction]（可挂起——本地未命中的 smartwash:// 会查后端字典缓存）；
 * - [navigate]：按动作导航（NativeRoute → 路由跳转；WebViewUrl → 壳层 WebView 页；Unknown → Toast）。
 */
@Singleton
class DeepLinkHandler @Inject constructor(
    private val deepLinkRegistry: DeepLinkRegistry,
    private val deepLinkRepository: DeepLinkRepository,
) {

    /**
     * 解析深链：先走本地解析（web 内建分支 + 各域贡献者注册表）；未命中且是
     * smartwash:// 时查后端路由字典（首次使用拉取 + 内存缓存，失败静默）判断
     * host 是否合法：
     * - host 在字典中且启用且面向 Android（platform 1/3）→ 合法但端上未注册，
     *   属端上版本能力缺口，仍回 Unknown（导航语义一致，升级引导留待后续迭代）；
     * - 字典不存在/已停用 → 同样 Unknown。
     * 本步顺带完成字典预热，日志留痕便于排查线上未知 host。
     */
    suspend fun resolve(uri: String): DeepLinkAction {
        val action = DeepLinkParser.parse(uri, deepLinkRegistry::lookup)
        if (action !is DeepLinkAction.Unknown) return action

        // http/https 恒为 WebView 分支不会 Unknown；仅 smartwash:// 有「字典合法性」概念
        val host = DeepLinkParser.hostOf(uri) ?: return action
        val routes = try {
            deepLinkRepository.getRoutes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 字典失败已在仓库层静默（仓库层 warn 留痕），此处兜意外实现变更破坏 resolve 契约，
            // debug 留痕避免与仓库层双记 warn
            AppLogger.debug(TAG, "深链字典查询意外异常（resolve 契约防御分支），按空字典兜底", e)
            emptyList()
        }
        val hostKnown = routes.any {
            it.host.equals(host, ignoreCase = true) &&
                it.enabled == DeeplinkRouteDict.ENABLED &&
                (it.platform == DeeplinkRouteDict.PLATFORM_ANDROID ||
                    it.platform == DeeplinkRouteDict.PLATFORM_BOTH)
        }
        AppLogger.debug(
            TAG,
            "deeplink 本地未命中：host=$host 后端字典合法=$hostKnown " +
                "（合法属端上未注册能力缺口，仍按暂不支持提示）uri=$uri 已注册host=${deepLinkRegistry.hosts}"
        )
        return action
    }

    /**
     * 按动作导航；Unknown 统一 Toast「暂不支持该链接」（文案走壳层 strings）。
     * 路由串失配（贡献者路由串与各域 *Route 漂移）时 Navigation 抛
     * IllegalArgumentException，此处兜住降级为「暂不支持」提示，不让深链击穿导航栈崩溃。
     */
    fun navigate(navController: NavHostController, action: DeepLinkAction) {
        when (action) {
            is DeepLinkAction.NativeRoute -> navigateSafely(navController, action.route)

            is DeepLinkAction.WebViewUrl ->
                navigateSafely(
                    navController,
                    ShellRoute.WebView(
                        url = action.url,
                        title = action.title.orEmpty(),
                        chrome = GameChrome.normalize(GameChrome.FULL),
                    )
                )

            is DeepLinkAction.Unknown -> toastUnsupported(navController)
        }
    }

    /**
     * 深链登录守卫判定（P-2001）：动作是否指向公开页面（未登录可放行）。
     *
     * 公开范围（逐 host 核对页面入口的数据消费后圈定）：
     * - [DeepLinkAction.WebViewUrl]：内置浏览器分支（http/https 与 smartwash://web），
     *   WebView 页自身不消费应用鉴权接口；
     * - [DeepLinkAction.Unknown]：仅 Toast「暂不支持」，无导航，无拦截意义；
     * - NativeRoute 中 host ∈ [PUBLIC_DEEP_LINK_HOSTS]：login / register（认证页自身，
     *   拦截即循环跳登录）与 lab（解压实验舱，纯本地玩具零网络请求）。
     *
     * 其余 host（pickup / ai-work / order / coupon / laundry / recharge / recharge-record /
     * divination / divination-history / game-center / review / subscription / dining /
     * messages / points / toolbox-short-code / toolbox-secret / toolbox-asset-tag / t /
     * delivery / setting）对应页面入口均消费 /web/auth 鉴权接口或依赖会话语义，
     * 未登录不放行，由壳层落登录页。
     *
     * @param uri 原始深链串（NativeRoute 的 host 自此处提取；host 不可提取时按受保护兜底）
     */
    fun isPublicAction(action: DeepLinkAction, uri: String): Boolean = when (action) {
        is DeepLinkAction.WebViewUrl -> true
        is DeepLinkAction.Unknown -> true
        is DeepLinkAction.NativeRoute ->
            DeepLinkParser.hostOf(uri)?.let { it in PUBLIC_DEEP_LINK_HOSTS } ?: false
    }

    private fun navigateSafely(navController: NavHostController, route: SmartWashRoute) {
        try {
            navController.navigate(route)
        } catch (e: IllegalArgumentException) {
            AppLogger.warn(TAG, "深链路由未注册：route=$route（贡献者路由串与各域 Route 漂移？）")
            toastUnsupported(navController)
        }
    }

    private fun toastUnsupported(navController: NavHostController) {
        val context = navController.context
        Toast.makeText(
            context,
            context.getString(R.string.deeplink_unsupported),
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private const val TAG = "DeepLinkHandler"

        /** 公开深链 host 白名单（未登录可放行；其余 host 未登录由壳层拦截落登录页，P-2001） */
        private val PUBLIC_DEEP_LINK_HOSTS = setOf("web", "login", "register", "lab")
    }
}
