package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 轮询调度器 JVM 单测（锚点）：基准间隔、连续失败指数退避封顶、成功重置。
 */
class DiningPollSchedulerTest {

    @Test
    fun `成功走基准间隔`() {
        val scheduler = DiningPollScheduler()
        assertEquals(3_000L, scheduler.nextDelayMs())
    }

    @Test
    fun `连续失败指数退避并封顶`() {
        val scheduler = DiningPollScheduler()

        scheduler.onFailure()
        assertEquals(3_000L, scheduler.nextDelayMs())
        scheduler.onFailure()
        assertEquals(6_000L, scheduler.nextDelayMs())
        scheduler.onFailure()
        assertEquals(12_000L, scheduler.nextDelayMs())
        // 24_000 超过封顶 15_000
        scheduler.onFailure()
        assertEquals(15_000L, scheduler.nextDelayMs())
        scheduler.onFailure()
        assertEquals(15_000L, scheduler.nextDelayMs())
    }

    @Test
    fun `成功重置退避`() {
        val scheduler = DiningPollScheduler()
        repeat(3) { scheduler.onFailure() }

        scheduler.onSuccess()

        assertEquals(3_000L, scheduler.nextDelayMs())
        assertEquals(0, scheduler.consecutiveFailures)
    }

    @Test
    fun `自定义间隔与封顶`() {
        val scheduler = DiningPollScheduler(intervalMs = 1_000L, maxBackoffMs = 2_500L)
        scheduler.onFailure()
        assertEquals(1_000L, scheduler.nextDelayMs())
        scheduler.onFailure()
        assertEquals(2_000L, scheduler.nextDelayMs())
        scheduler.onFailure()
        assertEquals(2_500L, scheduler.nextDelayMs())
    }
}
