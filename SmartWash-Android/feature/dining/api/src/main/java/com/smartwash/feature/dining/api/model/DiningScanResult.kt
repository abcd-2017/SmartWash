package com.smartwash.feature.dining.api.model

/**
 * 扫码内容经 dining 域归一化的结果（跨模块契约，消费方为壳层全局扫码页）。
 *
 * 三分语义与 [com.smartwash.feature.dining.logic.DiningJoinUrlParseResult] 对齐：
 * - [DeepLink]：命中本域加入链接，携带可直接经壳层深链体系分发的 smartwash:// 串；
 * - [NotDining]：非本域二维码（纯文本/其他 URL/其他应用深链），壳层按通用规则分发；
 * - [Malformed]：命中本域加入路径但参数非法（orderId/token 缺失或非法），壳层提示后继续扫码，
 *   不落入通用分发（避免坏 URL 被当普通链接打开 WebView 打 404）。
 */
sealed interface DiningScanResult {

    /** 命中本域加入链接：可分发的 smartwash:// 深链串（与 dining 深链贡献者同协议单源） */
    data class DeepLink(val uri: String) : DiningScanResult

    /** 非本域二维码，交还壳层通用分发 */
    data object NotDining : DiningScanResult

    /** 本域加入链接但结构非法，提示后继续扫码 */
    data object Malformed : DiningScanResult
}
