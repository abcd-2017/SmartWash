package com.smartwash.ui.page.scan

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.utils.deeplink.DeepLinkAction
import com.smartwash.deeplink.DeepLinkHandler
import com.smartwash.feature.dining.api.DiningApi
import com.smartwash.feature.dining.api.model.DiningScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 扫码识别结果的一次性分发事件（页面收集后执行副作用，副作用出组合期）。
 *
 * - [Navigate]：深链分发动作（NativeRoute / WebViewUrl / Unknown 均可能——Unknown 由
 *   [DeepLinkHandler.navigate] 统一 Toast「暂不支持」），页面导航后回退关闭扫码页；
 * - [CopyAndExit]：非链接纯文本，复制进剪贴板 + 提示后回退关闭扫码页；
 * - [Invalid]：命中加入链接但结构非法，仅提示，留在扫码页继续扫。
 */
sealed interface ScanDispatchEvent {

    /** 深链分发（解析已完成，页面只执行导航副作用） */
    data class Navigate(val action: DeepLinkAction) : ScanDispatchEvent

    /** 纯文本复制后退出扫码页 */
    data class CopyAndExit(val text: String) : ScanDispatchEvent

    /** 无效内容提示（留页继续扫码） */
    data class Invalid(val messageRes: Int) : ScanDispatchEvent
}

/**
 * 全局扫一扫 ViewModel：识别 → 归一化 → 分发，自身不承接任何业务。
 *
 * 归一化分两步（扫码结果是「输入」，业务承接在各域深链与壳层页面）：
 * 1. dining 域拦截（[DiningApi.normalizeScanToDeepLink]）：命中本域加入链接返回
 *    smartwash:// 深链串——加入 URL 识别知识留在 dining 域，壳层零业务感知；
 * 2. 壳层通用分发（[DeepLinkHandler.resolve]）：smartwash:// 走深链贡献者，
 *    http(s) 走内置 WebView，其余按纯文本复制兜底。
 *
 * 扫码去抖：相机连续帧对同一内容重复回调，同一原始内容在窗口内只处理一次；
 * 已分发成功后置 once 标志（页面即将关闭，不再处理后续帧）。
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val diningApi: DiningApi,
    private val deepLinkHandler: DeepLinkHandler,
) : ViewModel() {

    private val _events = Channel<ScanDispatchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 已完成分发（导航/复制事件已发出）：后续帧一律忽略，防 pop 前二次触发 */
    private var dispatched = false

    /** 扫码去抖：同一原始内容在窗口内只处理一次 */
    private var lastScannedRaw: String? = null
    private var lastScannedAt: Long = 0L

    /** 相机识别回调（页面分析器触发；去抖 + once 守卫） */
    fun onCodeScanned(raw: String) {
        if (dispatched) return
        if (raw.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (raw == lastScannedRaw && now - lastScannedAt < SCAN_DEBOUNCE_MS) return
        lastScannedRaw = raw
        lastScannedAt = now

        viewModelScope.launch {
            val event = normalize(raw)
            if (event !is ScanDispatchEvent.Invalid) dispatched = true
            _events.send(event)
        }
    }

    /**
     * 执行深链导航副作用（页面事件收集后调用；模式对齐 WorkbenchViewModel.navigate）：
     * Unknown 动作由 [DeepLinkHandler.navigate] 统一 Toast「暂不支持」，页面随后回退扫码页。
     */
    fun navigate(navController: NavHostController, action: DeepLinkAction) {
        deepLinkHandler.navigate(navController, action)
    }

    /** 归一化：dining 域拦截 → 通用深链分发 → 纯文本复制兜底 */
    private suspend fun normalize(raw: String): ScanDispatchEvent {
        when (val dining = diningApi.normalizeScanToDeepLink(raw)) {
            is DiningScanResult.DeepLink -> return navigateEvent(dining.uri)
            DiningScanResult.Malformed -> return ScanDispatchEvent.Invalid(R.string.scan_invalid_link)
            DiningScanResult.NotDining -> Unit // 落通用分发
        }
        val looksLikeLink = raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true) ||
            raw.startsWith(DEEP_LINK_SCHEME, ignoreCase = true)
        if (!looksLikeLink) return ScanDispatchEvent.CopyAndExit(raw)
        return navigateEvent(raw)
    }

    /** 深链解析（可挂起：未命中 host 会查后端字典预热缓存），动作交页面导航 */
    private suspend fun navigateEvent(uri: String): ScanDispatchEvent =
        ScanDispatchEvent.Navigate(deepLinkHandler.resolve(uri))

    private companion object {
        /** 同一二维码去抖窗口（毫秒） */
        const val SCAN_DEBOUNCE_MS = 1500L

        /** 应用自有深链 scheme（与 DeepLinkParser.SCHEME 同口径；壳层引用 common 常量亦可） */
        const val DEEP_LINK_SCHEME = "smartwash://"
    }
}
