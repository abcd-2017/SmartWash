package com.smartwash.feature.deeplink

import java.net.URLDecoder

/**
 * 深链解析器（纯函数 object，零 Android 依赖，JVM 单测全覆盖——解析全程不抛异常）。
 *
 * 三分支：
 * 1. `http://` / `https://` → [DeepLinkAction.WebViewUrl]（URI 原样，不做改写/解码）；
 * 2. `smartwash://`（scheme 与 host 大小写不敏感）host 分支：
 *    - `web`：query `url` 必填、`title` 可选（均百分号解码），url 必须为 http/https；
 *    - `t/{code}`：path 段 code 为 4-16 位字母数字，映射工具箱短链详情路由；
 *    - 其余 host → 查传入的 [DeepLinkRegistry] 本地注册表；
 * 3. 其余 scheme / 形态非法 → [DeepLinkAction.Unknown]。
 */
object DeepLinkParser {

    /** 自有 scheme（大小写不敏感） */
    const val SCHEME = "smartwash"

    /** 内建 host：WebView 分支（query 携带 url/title），不经注册表 */
    const val HOST_WEB = "web"

    /** 内建 host：短链码分支（path 段携带 code），不经注册表 */
    const val HOST_T = "t"

    /** scheme 分隔符 */
    private const val SCHEME_SEPARATOR = "://"

    /** 短链码规则：4-16 位字母数字（任务约定；URL path 段不含 -_ 与工具箱自定义别名规则区分） */
    private val SHORT_CODE_REGEX = Regex("^[0-9a-zA-Z]{4,16}$")

    /** 解析入口：任何输入（含畸形 URI）都不抛异常，失败一律 [DeepLinkAction.Unknown] */
    fun parse(uri: String, registry: DeepLinkRegistry): DeepLinkAction {
        if (uri.isBlank()) return DeepLinkAction.Unknown(uri)
        return try {
            parseInternal(uri.trim(), registry)
        } catch (e: Exception) {
            // 兜底：解析逻辑自身的意外异常也降级为 Unknown，保证调用方无需 try-catch
            DeepLinkAction.Unknown(uri)
        }
    }

    /**
     * 提取 smartwash:// 的 host（供 Handler 未命中时查后端字典兜底）；
     * 非 smartwash scheme 或无 host 返回 null。
     */
    fun hostOf(uri: String): String? {
        val trimmed = uri.trim()
        val prefix = SCHEME + SCHEME_SEPARATOR
        if (!trimmed.lowercase().startsWith(prefix)) return null
        val rest = trimmed.substring(prefix.length)
        val queryStart = rest.indexOf('?')
        val pathPart = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        return pathPart.substringBefore('/').lowercase().ifBlank { null }
    }

    // ---------- 内部实现 ----------

    private fun parseInternal(uri: String, registry: DeepLinkRegistry): DeepLinkAction {
        val schemeEnd = uri.indexOf(SCHEME_SEPARATOR)
        if (schemeEnd <= 0) return DeepLinkAction.Unknown(uri)
        val scheme = uri.substring(0, schemeEnd).lowercase()
        val rest = uri.substring(schemeEnd + SCHEME_SEPARATOR.length)
        if (rest.isEmpty()) return DeepLinkAction.Unknown(uri)
        return when (scheme) {
            "http", "https" -> DeepLinkAction.WebViewUrl(url = uri, title = null)
            SCHEME -> parseSmartwash(rest, uri, registry)
            else -> DeepLinkAction.Unknown(uri)
        }
    }

    /** smartwash://host/path?query 的 host 分支 */
    private fun parseSmartwash(rest: String, rawUri: String, registry: DeepLinkRegistry): DeepLinkAction {
        val queryStart = rest.indexOf('?')
        val pathPart = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        val queryPart = if (queryStart >= 0) rest.substring(queryStart + 1) else null
        // 过滤空段：容忍 smartwash://web/ 形式的尾斜杠
        val segments = pathPart.split('/').filter { it.isNotEmpty() }
        val host = segments.firstOrNull()?.lowercase() ?: return DeepLinkAction.Unknown(rawUri)
        val params = parseQuery(queryPart)
        return when (host) {
            HOST_WEB -> parseWeb(params, rawUri)
            HOST_T -> parseShortCode(segments, rawUri)
            else -> {
                val route = registry.routeFor(host, params)
                    ?: return DeepLinkAction.Unknown(rawUri)
                DeepLinkAction.NativeRoute(route)
            }
        }
    }

    /** web 分支：query url 必填且必须 http/https，title 可选（值已在 parseQuery 解码） */
    private fun parseWeb(params: Map<String, String>, rawUri: String): DeepLinkAction {
        val url = params["url"]
        if (url.isNullOrBlank()) return DeepLinkAction.Unknown(rawUri)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return DeepLinkAction.Unknown(rawUri)
        }
        val title = params["title"]?.takeIf { it.isNotBlank() }
        return DeepLinkAction.WebViewUrl(url = url, title = title)
    }

    /** t 分支：仅接受 t/{code} 两段形态，code 4-16 位字母数字 */
    private fun parseShortCode(segments: List<String>, rawUri: String): DeepLinkAction {
        if (segments.size != 2) return DeepLinkAction.Unknown(rawUri)
        val code = segments[1]
        if (!SHORT_CODE_REGEX.matches(code)) return DeepLinkAction.Unknown(rawUri)
        // 详情路由其余字段走注册缺省值（见 :feature:toolbox 的 ToolboxGraph 注册形态）
        return DeepLinkAction.NativeRoute(
            DeepLinkRoutes.TOOLBOX_DETAIL + "?code=" + code
        )
    }

    /** query 串解析：key 原样（自有 scheme 的 key 均为 ASCII），value 百分号解码，畸形参数丢弃 */
    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val params = mutableMapOf<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            val rawValue = if (eq >= 0) pair.substring(eq + 1) else ""
            decodePercent(rawValue)?.let { params[key] = it }
        }
        return params
    }

    /**
     * 百分号解码（UTF-8）。字面 `+` 不转空格——URI query 语义中 + 是普通字符，
     * 与表单编码区分；解码失败（畸形 % 序列）返回 null 由调用方丢弃。
     */
    private fun decodePercent(input: String): String? = try {
        if (input.contains('%')) {
            // URLDecoder 会把 + 解成空格，先把字面 + 保护为 %2B 再解码，两不误伤
            URLDecoder.decode(input.replace("+", "%2B"), Charsets.UTF_8.name())
        } else {
            input
        }
    } catch (e: Exception) {
        null
    }
}
