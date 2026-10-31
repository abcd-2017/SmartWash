package com.smartwash.common.utils.deeplink

import java.net.URLDecoder

/**
 * 深链解析器（纯函数 object，零路由知识、零 Android 依赖，JVM 单测全覆盖——解析全程不抛异常）。
 *
 * 内建判定仅两类，其余全部交给 [routeLookup]（host → 路由串，由壳层合并各域贡献者实现）：
 * 1. `http://` / `https://` → [DeepLinkAction.WebViewUrl]（URI 原样，不做改写/解码）；
 * 2. `smartwash://web`：query `url` 必填、`title` 可选（均百分号解码），url 必须为 http/https；
 * 3. 其余 host → [routeLookup](host, pathSegments, query)：
 *    命中 → [DeepLinkAction.NativeRoute]；未命中 → [DeepLinkAction.Unknown]。
 *    `t/{code}` 等带 path 段的 host 同样走 lookup（如 toolbox 贡献者自校验 code 格式）。
 * 非 smartwash scheme / 畸形输入 → [DeepLinkAction.Unknown]。
 */
object DeepLinkParser {

    /** 自有 scheme（大小写不敏感） */
    const val SCHEME = "smartwash"

    /** 内建 host：WebView 分支（query 携带 url/title），不经 routeLookup */
    const val HOST_WEB = "web"

    /** scheme 分隔符 */
    private const val SCHEME_SEPARATOR = "://"

    /**
     * 解析入口：任何输入（含畸形 URI）都不抛异常，失败一律 [DeepLinkAction.Unknown]。
     *
     * @param uri 待解析深链
     * @param routeLookup host → 路由串查找（host 已小写；pathSegments 保留原始大小写；
     *   query value 已百分号解码）。由壳层 DeepLinkRegistry 合并各域贡献者后提供。
     */
    fun parse(
        uri: String,
        routeLookup: (host: String, pathSegments: List<String>, query: Map<String, String>) -> String?,
    ): DeepLinkAction {
        if (uri.isBlank()) return DeepLinkAction.Unknown(uri)
        return try {
            parseInternal(uri.trim(), routeLookup)
        } catch (e: Exception) {
            // 兜底：解析逻辑自身的意外异常也降级为 Unknown，保证调用方无需 try-catch
            DeepLinkAction.Unknown(uri)
        }
    }

    /**
     * 提取 smartwash:// 的 host（供壳层 Handler 未命中时查后端字典兜底）；
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

    private fun parseInternal(
        uri: String,
        routeLookup: (host: String, pathSegments: List<String>, query: Map<String, String>) -> String?,
    ): DeepLinkAction {
        val schemeEnd = uri.indexOf(SCHEME_SEPARATOR)
        if (schemeEnd <= 0) return DeepLinkAction.Unknown(uri)
        val scheme = uri.substring(0, schemeEnd).lowercase()
        val rest = uri.substring(schemeEnd + SCHEME_SEPARATOR.length)
        if (rest.isEmpty()) return DeepLinkAction.Unknown(uri)
        return when (scheme) {
            "http", "https" -> DeepLinkAction.WebViewUrl(url = uri, title = null)
            SCHEME -> parseSmartwash(rest, uri, routeLookup)
            else -> DeepLinkAction.Unknown(uri)
        }
    }

    /** smartwash://host/path?query：web 为内建分支，其余 host 全部交给 routeLookup */
    private fun parseSmartwash(
        rest: String,
        rawUri: String,
        routeLookup: (host: String, pathSegments: List<String>, query: Map<String, String>) -> String?,
    ): DeepLinkAction {
        val queryStart = rest.indexOf('?')
        val pathPart = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        val queryPart = if (queryStart >= 0) rest.substring(queryStart + 1) else null
        // 过滤空段：容忍 smartwash://host/ 形式的尾斜杠
        val segments = pathPart.split('/').filter { it.isNotEmpty() }
        val host = segments.firstOrNull()?.lowercase() ?: return DeepLinkAction.Unknown(rawUri)
        val params = parseQuery(queryPart)
        if (host == HOST_WEB) return parseWeb(params, rawUri)
        // host 之外剩余 path 段原样下传（t/{code} 的 code 大小写敏感，由贡献者自行校验）
        val route = routeLookup(host, segments.drop(1), params)
            ?: return DeepLinkAction.Unknown(rawUri)
        return DeepLinkAction.NativeRoute(route)
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
