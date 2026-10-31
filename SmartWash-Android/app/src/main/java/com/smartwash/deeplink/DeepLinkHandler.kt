package com.smartwash.deeplink

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.utils.deeplink.DeepLinkAction
import com.smartwash.common.utils.deeplink.DeepLinkParser
import com.smartwash.network.api.DeeplinkRouteDict
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
            // 字典失败已在仓库层静默，此处兜底防意外实现变更破坏 resolve 契约
            emptyList()
        }
        val hostKnown = routes.any {
            it.host.equals(host, ignoreCase = true) &&
                it.enabled == DeeplinkRouteDict.ENABLED &&
                (it.platform == DeeplinkRouteDict.PLATFORM_ANDROID ||
                    it.platform == DeeplinkRouteDict.PLATFORM_BOTH)
        }
        Log.d(
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
                navigateSafely(navController, buildWebViewRoute(action.url, action.title))

            is DeepLinkAction.Unknown -> toastUnsupported(navController)
        }
    }

    private fun navigateSafely(navController: NavHostController, route: String) {
        try {
            navController.navigate(route)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "深链路由未注册：route=$route（贡献者路由串与各域 Route 漂移？）")
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

    /**
     * WebView 路由构造（注册形态 = ShellRoute.WEB_VIEW + "?url={url}&title={title}"，
     * 单一事实来源在 common:ui 的 [ShellRoute]；url/title 必须经 Uri.encode 编码）。
     */
    private fun buildWebViewRoute(url: String, title: String?): String {
        val builder = StringBuilder(ShellRoute.WEB_VIEW)
            .append("?url=").append(Uri.encode(url))
        title?.takeIf { it.isNotBlank() }?.let {
            builder.append("&title=").append(Uri.encode(it))
        }
        return builder.toString()
    }

    companion object {
        private const val TAG = "DeepLinkHandler"
    }
}
