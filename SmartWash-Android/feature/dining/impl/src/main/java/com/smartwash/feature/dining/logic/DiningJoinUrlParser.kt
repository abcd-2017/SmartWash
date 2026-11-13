package com.smartwash.feature.dining.logic

import java.net.URI

/**
 * 扫码加入链接解析（纯 Kotlin，无 Android 依赖，JVM 单测锚点）。
 *
 * spec 契约：二维码内容 = `{origin}/dining/join/{orderId}?t={join_token}`（由
 * [DiningJoinUrlBuilder] 构造）。端内扫码不走 DeepLinkParser 的 https WebView 分支，
 * 由本函数自解析：仅识别 dining 加入路径，[orderId] 必须为正整数，[token] 必填。
 *
 * 大小写策略：scheme/host 大小写不敏感（[URI] 保留原始大小写，解析时统一小写）；
 * 路径段大小写敏感（spec 固定 `/dining/join/`，改写即视为非本应用码）；查询键 `t`
 * 大小写不敏感（短链/扫码器可能改写键名）。
 */
object DiningJoinUrlParser {

    /** 加入路径前缀（与 [DiningJoinUrlBuilder] 同址单源，改一处需同步） */
    private const val JOIN_PATH_PREFIX = "/dining/join/"

    /** 加入凭证查询键（大小写不敏感匹配） */
    private const val TOKEN_QUERY_KEY = "t"

    /**
     * 解析扫码原始内容。
     *
     * @return [DiningJoinUrlParseResult.Join] 合法加入链接；[NotDining] 非本应用码
     * （空串/纯文本/非 http 族 scheme/路径不符）；[Malformed] 命中加入路径但结构非法
     * （orderId 缺失/非正整数/多余路径、token 缺失或空白）。
     */
    fun parse(raw: String?): DiningJoinUrlParseResult {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return DiningJoinUrlParseResult.NotDining
        val uri = runCatching { URI(value) }.getOrNull() ?: return DiningJoinUrlParseResult.NotDining

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return DiningJoinUrlParseResult.NotDining

        val path = uri.path ?: return DiningJoinUrlParseResult.NotDining
        if (!path.startsWith(JOIN_PATH_PREFIX)) return DiningJoinUrlParseResult.NotDining

        val orderIdText = path.removePrefix(JOIN_PATH_PREFIX)
        if (orderIdText.isEmpty() || orderIdText.contains('/')) return DiningJoinUrlParseResult.Malformed
        val orderId = orderIdText.toLongOrNull() ?: return DiningJoinUrlParseResult.Malformed
        if (orderId <= 0L) return DiningJoinUrlParseResult.Malformed

        val token = uri.rawQuery?.split('&')
            ?.mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = pair.substring(0, index)
                if (!key.equals(TOKEN_QUERY_KEY, ignoreCase = true)) return@mapNotNull null
                pair.substring(index + 1)
            }
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (token.isEmpty()) return DiningJoinUrlParseResult.Malformed

        return DiningJoinUrlParseResult.Join(orderId = orderId, token = token)
    }
}

/** 扫码加入链接解析结果（失败原因三分：合法 / 非本应用码 / 本应用路径结构非法） */
sealed interface DiningJoinUrlParseResult {

    /** 合法加入链接：订单 ID + 加入凭证 */
    data class Join(val orderId: Long, val token: String) : DiningJoinUrlParseResult

    /** 非本应用二维码（空串/纯文本/非 http 族 scheme/路径不符） */
    data object NotDining : DiningJoinUrlParseResult

    /** 命中加入路径但结构非法（orderId/token 缺失或非法） */
    data object Malformed : DiningJoinUrlParseResult
}
