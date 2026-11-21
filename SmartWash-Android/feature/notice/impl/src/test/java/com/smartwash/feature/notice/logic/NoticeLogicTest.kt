package com.smartwash.feature.notice.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 站内信纯逻辑 JVM 单测（类型枚举映射 + 时间解析分桶，时钟注入固定「今天」）。
 *
 * 锚点契约：
 * - NoticeType.from 对未收录/缺失类型一律落 UNKNOWN，UI 回退通用文案，不崩不空白；
 * - noticeTimeParts 分桶按日期差：0=今天、1=昨天、同年更早=本年、跨年=更早；
 * - null/空白/格式漂移一律返回 null（UI 隐藏时间而非展示垃圾串）；
 * - 兼容历史空格分隔格式（空格 → 'T' 二次解析）。
 */
class NoticeLogicTest {

    /** 固定时钟：2026-11-21 10:00（Asia/Shanghai），「今天」恒定 */
    private val clock = Clock.fixed(
        Instant.parse("2026-11-21T02:00:00Z"),
        ZoneId.of("Asia/Shanghai"),
    )

    // ==================== NoticeType ====================

    @Test
    fun `已知类型字符串映射到对应枚举`() {
        assertEquals(NoticeType.SYSTEM, NoticeType.from("SYSTEM"))
        assertEquals(NoticeType.ANNOUNCEMENT, NoticeType.from("ANNOUNCEMENT"))
    }

    @Test
    fun `未收录与缺失类型一律落 UNKNOWN`() {
        assertEquals(NoticeType.UNKNOWN, NoticeType.from("PUSH"))
        assertEquals(NoticeType.UNKNOWN, NoticeType.from("system"))
        assertEquals(NoticeType.UNKNOWN, NoticeType.from(""))
        assertEquals(NoticeType.UNKNOWN, NoticeType.from(null))
    }

    @Test
    fun `类型文案资源契约 已知项带 labelRes`() {
        assertEquals(com.smartwash.feature.notice.R.string.notice_type_system, NoticeType.SYSTEM.labelRes)
        assertEquals(com.smartwash.feature.notice.R.string.notice_type_announcement, NoticeType.ANNOUNCEMENT.labelRes)
        assertEquals(com.smartwash.feature.notice.R.string.notice_type_unknown, NoticeType.UNKNOWN.labelRes)
    }

    // ==================== noticeTimeParts：分桶 ====================

    @Test
    fun `今天的时间分桶为 TODAY 并给出 HHmm 分量`() {
        val parts = noticeTimeParts("2026-11-21T08:30:00", clock)!!
        assertEquals(NoticeTimeBucket.TODAY, parts.bucket)
        assertEquals("08:30", parts.time)
        assertEquals(11, parts.month)
        assertEquals(21, parts.day)
        assertEquals(2026, parts.year)
    }

    @Test
    fun `昨天的时间分桶为 YESTERDAY`() {
        val parts = noticeTimeParts("2026-11-20T23:59:00", clock)!!
        assertEquals(NoticeTimeBucket.YESTERDAY, parts.bucket)
        assertEquals("23:59", parts.time)
    }

    @Test
    fun `同年更早分桶为 THIS_YEAR`() {
        assertEquals(NoticeTimeBucket.THIS_YEAR, noticeTimeParts("2026-01-15T09:00:00", clock)!!.bucket)
        assertEquals(NoticeTimeBucket.THIS_YEAR, noticeTimeParts("2026-11-19T00:00:00", clock)!!.bucket)
    }

    @Test
    fun `跨年分桶为 OLDER`() {
        assertEquals(NoticeTimeBucket.OLDER, noticeTimeParts("2025-12-31T09:00:00", clock)!!.bucket)
        assertEquals(NoticeTimeBucket.OLDER, noticeTimeParts("2024-06-01T00:00:00", clock)!!.bucket)
    }

    // ==================== noticeTimeParts：容错 ====================

    @Test
    fun `历史空格分隔格式兼容解析`() {
        val parts = noticeTimeParts("2026-11-21 08:30:00", clock)!!
        assertEquals(NoticeTimeBucket.TODAY, parts.bucket)
        assertEquals("08:30", parts.time)
    }

    @Test
    fun `缺失空白或非法格式一律返回 null`() {
        assertNull(noticeTimeParts(null, clock))
        assertNull(noticeTimeParts("", clock))
        assertNull(noticeTimeParts("   ", clock))
        assertNull(noticeTimeParts("2026/11/21 08:30", clock))
        assertNull(noticeTimeParts("not-a-time", clock))
        assertNull(noticeTimeParts("2026-11-21", clock)) // 纯日期（缺时间部分）解析失败 → null
    }

    @Test
    fun `时间格式化分量为两位 HHmm`() {
        assertEquals("00:05", noticeTimeParts("2026-11-21T00:05:00", clock)!!.time)
        assertEquals("23:08", noticeTimeParts("2026-11-21T23:08:09", clock)!!.time)
    }
}
