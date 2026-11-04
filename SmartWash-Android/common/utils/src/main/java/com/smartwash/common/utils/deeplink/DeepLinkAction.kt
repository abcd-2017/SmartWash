package com.smartwash.common.utils.deeplink

import com.smartwash.common.utils.navigation.SmartWashRoute

/**
 * 深链解析结果（三态，密封类）：
 * - [NativeRoute]：routeLookup 命中，携带类型安全路由对象；
 * - [WebViewUrl]：http(s) 直链或 smartwash://web 分支，由壳层 WebView 页承载；
 * - [Unknown]：非法或未注册，UI 侧统一提示「暂不支持该链接」。
 */
sealed class DeepLinkAction {

    /** 原生路由跳转（route 为各域 @Serializable 路由对象，直接 navigate） */
    data class NativeRoute(val route: SmartWashRoute) : DeepLinkAction()

    /**
     * 内置浏览器打开。
     * @param url 网页地址（调用方保证 http/https）
     * @param title 页面标题，null/blank 时由 WebView 页兜底「详情页」
     */
    data class WebViewUrl(val url: String, val title: String? = null) : DeepLinkAction()

    /** 不支持：scheme 非法、参数缺失、host 未注册等 */
    data class Unknown(val uri: String) : DeepLinkAction()
}
