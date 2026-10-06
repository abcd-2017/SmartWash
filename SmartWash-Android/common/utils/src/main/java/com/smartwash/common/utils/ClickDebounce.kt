package com.smartwash.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * 点击防抖器（节流语义）：首次调用立即放行，时间窗口内的后续调用一律丢弃；
 * 被丢弃的调用不刷新窗口，窗口自首次放行起算。
 *
 * 用于阻断快速连点导致的重复导航（连点可在转场期命中残影页，弹空返回栈）
 * 与重复提交（支付/充值/预约等副作用请求）。非线程安全，仅供主线程 UI 事件回调使用。
 *
 * 时钟经构造注入便于单测；生产使用默认 [System.currentTimeMillis]。
 */
class ClickDebouncer(
    private val intervalMs: Long = DEFAULT_CLICK_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // 取半值初始化，避免 Long.MIN_VALUE 参与减法溢出
    private var lastAllowedAt = Long.MIN_VALUE / 2

    /** 判断本次点击是否放行；放行的同时记录时间戳。 */
    fun tryAcquire(): Boolean {
        val now = clock()
        if (now - lastAllowedAt >= intervalMs) {
            lastAllowedAt = now
            return true
        }
        return false
    }

    companion object {
        /** 默认防抖窗口：覆盖页面转场时长（临界阻尼弹簧约 350-500ms 收敛），阻断转场残影页误触 */
        const val DEFAULT_CLICK_INTERVAL_MS = 500L

        /** 有副作用动作（网络提交 / 资金操作 / 高频误触导航入口）建议窗口：留出更长余量 */
        const val ACTION_CLICK_INTERVAL_MS = 800L
    }
}

/**
 * 组合期创建防抖点击回调，供 [androidx.compose.foundation.clickable]/[androidx.compose.material3.Button]
 * 等 onClick 直接使用：`onClick = rememberDebouncedClick { ... }`。
 *
 * 语义：窗口期内重复调用被静默丢弃（不排队、不延后），始终执行最新的 [onClick]；
 * [enabled] 为 false 时点击直接忽略，且不消耗防抖窗口。
 */
@Composable
fun rememberDebouncedClick(
    intervalMs: Long = ClickDebouncer.DEFAULT_CLICK_INTERVAL_MS,
    enabled: Boolean = true,
    onClick: () -> Unit,
): () -> Unit {
    val debouncer = remember(intervalMs) { ClickDebouncer(intervalMs) }
    val latestOnClick by rememberUpdatedState(onClick)
    val latestEnabled by rememberUpdatedState(enabled)
    return remember(debouncer) {
        {
            if (latestEnabled && debouncer.tryAcquire()) {
                latestOnClick()
            }
        }
    }
}
