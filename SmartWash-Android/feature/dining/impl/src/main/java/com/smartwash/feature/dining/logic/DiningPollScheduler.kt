package com.smartwash.feature.dining.logic

/**
 * 轮询调度器（纯 Kotlin，无协程/Android 依赖，JVM 单测锚点）。
 * 只决定「下一次轮询前等多久」：成功走基准间隔，连续失败指数退避封顶。
 */
class DiningPollScheduler(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val maxBackoffMs: Long = MAX_BACKOFF_MS,
) {
    /** 连续失败次数（成功即清零） */
    var consecutiveFailures: Int = 0
        private set

    /** 轮询成功：重置退避 */
    fun onSuccess() {
        consecutiveFailures = 0
    }

    /** 轮询失败：退避计数 +1 */
    fun onFailure() {
        consecutiveFailures += 1
    }

    /** 距下次轮询的等待毫秒 */
    fun nextDelayMs(): Long {
        if (consecutiveFailures <= 0) return intervalMs
        val factor = 1L shl (consecutiveFailures - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
        return (intervalMs * factor).coerceAtMost(maxBackoffMs)
    }

    companion object {
        /** 基准轮询间隔（spec：点单页可见时约 3s） */
        const val DEFAULT_INTERVAL_MS = 3_000L

        /** 连续失败退避封顶 */
        const val MAX_BACKOFF_MS = 15_000L

        /** 退避倍数上限（2^6=64 倍，远超封顶，仅防移位溢出） */
        private const val MAX_BACKOFF_SHIFT = 6
    }
}
