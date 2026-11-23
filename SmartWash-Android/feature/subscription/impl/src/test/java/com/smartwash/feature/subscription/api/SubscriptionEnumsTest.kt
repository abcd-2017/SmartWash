package com.smartwash.feature.subscription.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 订阅域枚举映射 JVM 单测（契约 docs/subscription-api.md §一/§三）。
 *
 * 锚点契约：DTO 携带后端原值，枚举在 UI 层转换；未知值防御回落（active/month/renew），
 * 提交时回写 raw 原值，两端零语义漂移；终态（canceled/finished）只读。
 */
class SubscriptionEnumsTest {

    // ==================== SubscriptionStatus ====================

    @Test
    fun `状态 raw 与枚举一一对应`() {
        assertEquals("active", SubscriptionStatus.ACTIVE.raw)
        assertEquals("paused", SubscriptionStatus.PAUSED.raw)
        assertEquals("canceled", SubscriptionStatus.CANCELED.raw)
        assertEquals("finished", SubscriptionStatus.FINISHED.raw)
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.fromRaw("active"))
        assertEquals(SubscriptionStatus.PAUSED, SubscriptionStatus.fromRaw("paused"))
        assertEquals(SubscriptionStatus.CANCELED, SubscriptionStatus.fromRaw("canceled"))
        assertEquals(SubscriptionStatus.FINISHED, SubscriptionStatus.fromRaw("finished"))
    }

    @Test
    fun `未知与缺失状态 fromRaw 返回 null`() {
        assertNull(SubscriptionStatus.fromRaw(null))
        assertNull(SubscriptionStatus.fromRaw(""))
        assertNull(SubscriptionStatus.fromRaw("ACTIVE")) // 大小写敏感，不静默归一
        assertNull(SubscriptionStatus.fromRaw("archived"))
    }

    @Test
    fun `fromRawOrDefault 未知缺失一律回落 active`() {
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.fromRawOrDefault(null))
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.fromRawOrDefault("garbage"))
        assertEquals(SubscriptionStatus.FINISHED, SubscriptionStatus.fromRawOrDefault("finished")) // 合法值不回落
    }

    @Test
    fun `终态只读 仅 canceled 与 finished`() {
        assertTrue(SubscriptionStatus.CANCELED.isTerminal)
        assertTrue(SubscriptionStatus.FINISHED.isTerminal)
        assertFalse(SubscriptionStatus.ACTIVE.isTerminal)
        assertFalse(SubscriptionStatus.PAUSED.isTerminal)
    }

    // ==================== SubscriptionCycleType ====================

    @Test
    fun `周期类型 raw 映射 未知回落 month`() {
        assertEquals(SubscriptionCycleType.MONTH, SubscriptionCycleType.fromRaw("month"))
        assertEquals(SubscriptionCycleType.YEAR, SubscriptionCycleType.fromRaw("year"))
        assertEquals(SubscriptionCycleType.MONTH, SubscriptionCycleType.fromRaw("week"))
        assertEquals(SubscriptionCycleType.MONTH, SubscriptionCycleType.fromRaw(null))
        assertEquals(SubscriptionCycleType.MONTH, SubscriptionCycleType.fromRaw(""))
    }

    // ==================== SubscriptionReminderType ====================

    @Test
    fun `提醒类型 raw 映射 未知回落 renew`() {
        assertEquals(SubscriptionReminderType.RENEW, SubscriptionReminderType.fromRawOrDefault("renew"))
        assertEquals(SubscriptionReminderType.TRIAL_END, SubscriptionReminderType.fromRawOrDefault("trial_end"))
        assertEquals(SubscriptionReminderType.RENEW, SubscriptionReminderType.fromRawOrDefault("push"))
        assertEquals(SubscriptionReminderType.RENEW, SubscriptionReminderType.fromRawOrDefault(null))
    }

    // ==================== SubscriptionConstant 边界值契约 ====================

    @Test
    fun `业务边界值对齐后端 SaveSubscription 校验`() {
        assertEquals(1, SubscriptionConstant.CYCLE_INTERVAL_MIN)
        assertEquals(36, SubscriptionConstant.CYCLE_INTERVAL_MAX)
        assertEquals(0, SubscriptionConstant.REMIND_DAYS_MIN)
        assertEquals(60, SubscriptionConstant.REMIND_DAYS_MAX)
        assertEquals(1, SubscriptionConstant.FLAG_ON)
        assertEquals(0, SubscriptionConstant.FLAG_OFF)
        // 分页上限对齐后端 BaseSearchFrom（最大 100）
        assertTrue(SubscriptionConstant.PAGE_SIZE <= 100)
    }
}
