package com.smartwash.feature.review.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 复习模块展示换算 JVM 单测（纯 JVM 逻辑，无 Android 依赖）。
 *
 * 契约锚点：到期判断以服务端时刻为准，客户端本地时钟仅做展示换算；
 * 分钟以下进位为「已到期」；due 缺失/解析失败一律 Unknown，UI 隐藏而非展示垃圾串。
 */
class ReviewFormatTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 21, 12, 0, 0)

    // ==================== parseDateTime ====================

    @Test
    fun `ISO 时刻解析成功`() {
        assertEquals(LocalDateTime.of(2026, 11, 21, 8, 30, 0), ReviewFormat.parseDateTime("2026-11-21T08:30:00"))
    }

    @Test
    fun `时刻解析 缺失或非法返回 null`() {
        assertNull(ReviewFormat.parseDateTime(null))
        assertNull(ReviewFormat.parseDateTime(""))
        assertNull(ReviewFormat.parseDateTime("   "))
        assertNull(ReviewFormat.parseDateTime("2026-11-21 08:30:00")) // 空格分隔不支持
        assertNull(ReviewFormat.parseDateTime("not-a-date"))
    }

    // ==================== dueTextOf：到期时长量级 ====================

    @Test
    fun `已到期（过去时刻）判 DueNow`() {
        assertEquals(ReviewFormat.DueText.DueNow, ReviewFormat.dueTextOf("2026-11-21T11:59:00", now))
        assertEquals(ReviewFormat.DueText.DueNow, ReviewFormat.dueTextOf("2026-11-20T00:00:00", now))
    }

    @Test
    fun `到期时刻等于当前时刻判 DueNow（分钟以下进位）`() {
        assertEquals(ReviewFormat.DueText.DueNow, ReviewFormat.dueTextOf("2026-11-21T12:00:00", now))
        // 59 秒后：不足 1 分钟进位为已到期
        assertEquals(ReviewFormat.DueText.DueNow, ReviewFormat.dueTextOf("2026-11-21T12:00:59", now))
    }

    @Test
    fun `分钟级到期量级`() {
        assertEquals(ReviewFormat.DueText.InMinutes(1), ReviewFormat.dueTextOf("2026-11-21T12:01:00", now))
        assertEquals(ReviewFormat.DueText.InMinutes(30), ReviewFormat.dueTextOf("2026-11-21T12:30:30", now))
        assertEquals(ReviewFormat.DueText.InMinutes(59), ReviewFormat.dueTextOf("2026-11-21T12:59:00", now))
    }

    @Test
    fun `小时级到期量级（60 分钟进位为小时）`() {
        assertEquals(ReviewFormat.DueText.InHours(1), ReviewFormat.dueTextOf("2026-11-21T13:00:00", now))
        assertEquals(ReviewFormat.DueText.InHours(23), ReviewFormat.dueTextOf("2026-11-22T11:30:00", now))
    }

    @Test
    fun `天级到期量级（24 小时进位为天）`() {
        assertEquals(ReviewFormat.DueText.InDays(1), ReviewFormat.dueTextOf("2026-11-22T12:00:00", now))
        assertEquals(ReviewFormat.DueText.InDays(7), ReviewFormat.dueTextOf("2026-11-28T12:00:00", now))
        assertEquals(ReviewFormat.DueText.InDays(30), ReviewFormat.dueTextOf("2026-12-21T12:00:00", now))
    }

    @Test
    fun `到期时刻缺失或非法判 Unknown`() {
        assertEquals(ReviewFormat.DueText.Unknown, ReviewFormat.dueTextOf(null, now))
        assertEquals(ReviewFormat.DueText.Unknown, ReviewFormat.dueTextOf("", now))
        assertEquals(ReviewFormat.DueText.Unknown, ReviewFormat.dueTextOf("2026/11/22 12:00:00", now))
    }

    // ==================== createdDateOf：展示日期与回落 ====================

    @Test
    fun `createdAt 转展示日期 yyyy-MM-dd`() {
        assertEquals("2026-03-05", ReviewFormat.createdDateOf("2026-03-05T10:00:00", "未知"))
    }

    @Test
    fun `createdAt 缺失或非法回落占位符`() {
        assertEquals("占位", ReviewFormat.createdDateOf(null, "占位"))
        assertEquals("占位", ReviewFormat.createdDateOf("", "占位"))
        assertEquals("占位", ReviewFormat.createdDateOf("20260305", "占位"))
    }
}
