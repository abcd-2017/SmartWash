package com.smartwash.common.ui.navigation

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 壳层路由契约（T8.1）：无域归属、注册在 app 壳且被多域 feature 跳转的类型安全路由。
 *
 * 主页壳（Home）由 user-impl（登录/注册/资料保存成功进入、登出清栈）与
 * payment-impl（支付成功 popUpTo 回主页）跨模块消费——单一事实来源放公共层，
 * 依赖方向 feature → common 合法（feature 不得反向依赖 app 壳的 PageConstant）。
 * 域页面路由归各自 api 模块（UserRoute/OrderRoute/PaymentRoute/LaundryRoute/
 * CouponRoute）或 :feature:divination 的 DivRoute；app 壳留守页面常量在 PageConstant。
 *
 * 类型安全迁移：原 `object ShellRoute { const val HOME/WEB_VIEW }` 字符串常量改为
 * sealed interface + @Serializable 路由对象；@SerialName 固定路由串与迁移前一致
 * （"Home" / "WebView"），保证页面栈与 restoreState 行为等价。
 */
sealed interface ShellRoute : SmartWashRoute {

    /** 主页壳（HomePage，底部四 tab 容器；注册于壳层 shellGraph） */
    @Serializable
    @SerialName("Home")
    data object Home : ShellRoute

    /**
     * 内置浏览器页（壳层留守，注册于壳层 shellGraph；注册形态
     * "WebView?url={url}&title={title}&chrome={chrome}"，三参均有默认值故为 query）。
     * 消费方：app 壳 DeepLinkHandler 的 WebViewUrl 动作（深链 web 分支 / http(s) 直链）。
     */
    @Serializable
    @SerialName("WebView")
    data class WebView(
        val url: String = "",
        val title: String = "",
        // chrome 默认值与 app 壳 GameChrome.FULL 取值一致（common:ui 禁止反向依赖 app，故就地写字面量）
        val chrome: String = "full",
    ) : ShellRoute

    /**
     * 扫码页（壳层留守，注册于壳层 shellGraph；无参对象路由 "Scan"）。
     * 全局扫一扫入口（工作台常驻）：识别任意二维码后按内容分发
     * （smartwash:// 深链 / dining 加入链接 / http(s) H5 / 纯文本复制），
     * 不归属任何业务域——扫码结果是「输入」，业务承接在各域深链与壳层页面。
     */
    @Serializable
    @SerialName("Scan")
    data object Scan : ShellRoute
}
