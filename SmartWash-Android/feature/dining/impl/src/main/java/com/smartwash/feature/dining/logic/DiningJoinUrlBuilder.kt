package com.smartwash.feature.dining.logic

import java.net.URI

/**
 * 加入二维码内容构造（纯 Kotlin，无 Android 依赖，JVM 单测锚点）。
 *
 * spec 契约：二维码内容 = `https://{host}/dining/join/{orderId}?t={join_token}`。
 *
 * host 取舍（对齐现有体系）：取主网关 BASE_URL 的 origin（scheme://host[:port]）——
 * BASE_URL 是环境唯一真源（构建期 Gradle 属性注入，demo 明文 http、生产 -PbaseUrl=
 * https://your-domain.com/），二维码 host 与 App 实际访问后端用的地址天然同源，
 * 不新增任何硬编码地址（环境红线）；scheme 随环境，demo 下为 http（usesCleartextTraffic
 * 全局放行现状一致）。路径段 /dining/join/ 与端上深链 host "dining" 对齐（端内扫码侧
 * 按 path 识别 + brief 确认，不依赖域名本身）。
 *
 * 不用 ServerConfig.shortLinkBaseUrl：其语义为短链对外域名且位于 app 壳层（feature 模块
 * 依赖方向禁止反向读取）；本构造无需后端扩展 /web/config（Android 单端契约，不加端）。
 */
object DiningJoinUrlBuilder {

    /** 二维码路径前缀（与端上深链 host "dining" 对齐；端内扫码侧按此 path 识别加入链路） */
    private const val JOIN_PATH_PREFIX = "/dining/join/"

    /**
     * 构造二维码内容 URL。
     *
     * @param baseUrl 主网关 BASE_URL（@Named("baseUrl") 运行期注入）
     * @param orderId 订单 ID（创建响应 safeOrderId）
     * @param joinToken 加入凭证（创建响应；空白视为无凭证返回 null——无凭证的码无意义）
     * @return 二维码内容；baseUrl 畸形/非 http(s) 或 token 空白返回 null（页面降级提示）
     */
    fun buildJoinUrl(baseUrl: String, orderId: Long, joinToken: String): String? {
        val token = joinToken.trim()
        if (token.isEmpty()) return null
        val origin = originOf(baseUrl) ?: return null
        return origin + JOIN_PATH_PREFIX + orderId + "?t=" + token
    }

    /**
     * 从 baseUrl 提取 origin（scheme://host[:port]，去尾斜杠与路径）。
     * 仅接受 http/https（二维码内容必须是浏览器/端内扫码可识别的 http 族地址）；
     * host 缺失/畸形返回 null。
     */
    private fun originOf(baseUrl: String): String? {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        return buildString {
            append(scheme).append("://").append(host)
            if (uri.port > 0) append(':').append(uri.port)
        }
    }
}
