package com.smartwash.feature.subscription.logic

import com.smartwash.feature.subscription.api.SubscriptionCycleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 订阅表单与展示纯逻辑 JVM 单测（取消网址校验 / 日期金额换算 / 周期进度环）。
 *
 * 锚点契约：
 * - 取消网址对齐后端 SaveSubscription：http/https 且非空白（trim 后整串无空白）；
 * - 日期解析失败一律 null，UI 防御降级；formatDisplayDateTime 不可解析原样返回；
 * - 周期总天数 = 从下个续费日回退一期（与后端 SubscriptionRenewJob 推进方向互逆，
 *   月付/年付的月末 clamp 行为一致）；进度 = 已消耗/总天数，负余量视为走满 1f。
 */
class SubscriptionRulesTest {

    // ==================== isValidCancelUrl ====================

    @Test
    fun `合法 http 与 https 取消网址通过`() {
        assertTrue(SubscriptionRules.isValidCancelUrl("https://example.com/cancel"))
        assertTrue(SubscriptionRules.isValidCancelUrl("http://example.com"))
        assertTrue(SubscriptionRules.isValidCancelUrl("HTTPS://EXAMPLE.COM/A?b=1")) // 大小写不敏感
    }

    @Test
    fun `取消网址首尾空白被 trim 后校验`() {
        assertTrue(SubscriptionRules.isValidCancelUrl("  https://example.com/c  "))
        assertFalse(SubscriptionRules.isValidCancelUrl("https://example.com/a b")) // 内部空白拒绝
        assertFalse(SubscriptionRules.isValidCancelUrl("   "))
    }

    @Test
    fun `非法取消网址拒绝`() {
        assertFalse(SubscriptionRules.isValidCancelUrl(""))
        assertFalse(SubscriptionRules.isValidCancelUrl("ftp://example.com"))
        assertFalse(SubscriptionRules.isValidCancelUrl("https://"))           // 缺主机
        assertFalse(SubscriptionRules.isValidCancelUrl("example.com/cancel")) // 缺 scheme
    }

    // ==================== parseDate / formatDate ====================

    @Test
    fun `日期解析与格式化往返一致`() {
        val date = SubscriptionRules.parseDate("2026-11-21")!!
        assertEquals(LocalDate.of(2026, 11, 21), date)
        assertEquals("2026-11-21", SubscriptionRules.formatDate(date))
        assertEquals("2026-01-05", SubscriptionRules.formatDate(LocalDate.of(2026, 1, 5))) // 补零
    }

    @Test
    fun `日期解析失败或缺失返回 null`() {
        assertNull(SubscriptionRules.parseDate(null))
        assertNull(SubscriptionRules.parseDate(""))
        assertNull(SubscriptionRules.parseDate("   "))
        assertNull(SubscriptionRules.parseDate("2026/11/21"))  // 斜杠格式不支持
        assertNull(SubscriptionRules.parseDate("2026-13-01"))  // 月份越界
        assertNull(SubscriptionRules.parseDate("garbage"))
    }

    // ==================== parseDateTime / formatDisplayDateTime ====================

    @Test
    fun `后端时间戳解析为 LocalDateTime`() {
        assertEquals(
            java.time.LocalDateTime.of(2026, 11, 21, 8, 30, 0),
            SubscriptionRules.parseDateTime("2026-11-21 08:30:00"),
        )
        assertNull(SubscriptionRules.parseDateTime(null))
        assertNull(SubscriptionRules.parseDateTime("2026-11-21T08:30:00")) // ISO T 格式不是后端时间戳格式
    }

    @Test
    fun `时间戳格式化为展示格式 不可解析原样返回`() {
        assertEquals("2026-11-21 08:30", SubscriptionRules.formatDisplayDateTime("2026-11-21 08:30:00"))
        assertEquals("2026-01-05 00:07", SubscriptionRules.formatDisplayDateTime("2026-01-05 00:07:59"))
        assertEquals("", SubscriptionRules.formatDisplayDateTime(null)) // null → 空串
        assertEquals("bad-input", SubscriptionRules.formatDisplayDateTime("bad-input")) // 原样返回不崩
    }

    // ==================== localDateFromPickerMillis ====================

    @Test
    fun `DatePicker UTC 毫秒转 LocalDate`() {
        val millis = Instant.parse("2026-11-21T00:00:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 21), SubscriptionRules.localDateFromPickerMillis(millis))
        assertNull(SubscriptionRules.localDateFromPickerMillis(null))
    }

    // ==================== formatAmount ====================

    @Test
    fun `金额两位小数`() {
        assertEquals("12.50", SubscriptionRules.formatAmount(12.5))
        assertEquals("0.00", SubscriptionRules.formatAmount(0.0))
        assertEquals("123.46", SubscriptionRules.formatAmount(123.456)) // 四舍五入
        assertEquals("88.00", SubscriptionRules.formatAmount(88.0))
    }

    // ==================== cycleTotalDays ====================

    @Test
    fun `月付周期总天数 回退一期到上个续费日`() {
        assertEquals(30, SubscriptionRules.cycleTotalDays(SubscriptionCycleType.MONTH, 1, LocalDate.of(2026, 12, 1)))
        assertEquals(31, SubscriptionRules.cycleTotalDays(SubscriptionCycleType.MONTH, 1, LocalDate.of(2026, 1, 31)))
        // 双月付：2026-03-31 回退 2 个月（clamp 到 2026-01-31）→ 59 天
        assertEquals(59, SubscriptionRules.cycleTotalDays(SubscriptionCycleType.MONTH, 2, LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun `年付周期总天数 含闰年`() {
        // 2024-05-01 → 2026-05-01：区间含 2025/2026 两个非闰二月 = 365 + 365 = 730
        //（闰日 2024-02-29 在区间起点之前，不计入）
        assertEquals(730, SubscriptionRules.cycleTotalDays(SubscriptionCycleType.YEAR, 2, LocalDate.of(2026, 5, 1)))
        // 含闰二月的区间：2024-01-01 → 2025-01-01 = 366
        assertEquals(366, SubscriptionRules.cycleTotalDays(SubscriptionCycleType.YEAR, 1, LocalDate.of(2025, 1, 1)))
        assertEquals(365, SubscriptionRules.cycleTotalDays(SubscriptionCycleType.YEAR, 1, LocalDate.of(2027, 5, 1)))
    }

    // ==================== renewProgressFraction ====================

    @Test
    fun `续费进度为已消耗占总天数比例`() {
        // nextRenew = 2026-12-01（月付），总 30 天，剩 21 天 → 已消耗 9 天 → 0.3
        assertEquals(0.3f, SubscriptionRules.renewProgressFraction(SubscriptionCycleType.MONTH, 1, "2026-12-01", 21)!!, 1e-6f)
        // 恰好续费日当天：进度 0
        assertEquals(0f, SubscriptionRules.renewProgressFraction(SubscriptionCycleType.MONTH, 1, "2026-12-01", 30)!!, 1e-6f)
    }

    @Test
    fun `续费进度 已过期待推进视为走满`() {
        assertEquals(1f, SubscriptionRules.renewProgressFraction(SubscriptionCycleType.MONTH, 1, "2026-12-01", -3)!!, 1e-6f)
    }

    @Test
    fun `续费进度 异常余量钳制到 0 到 1`() {
        // daysLeft > total（契约漂移）：consumed 为负 → 钳 0
        assertEquals(0f, SubscriptionRules.renewProgressFraction(SubscriptionCycleType.MONTH, 1, "2026-12-01", 45)!!, 1e-6f)
    }

    @Test
    fun `续费进度 输入不可解析返回 null（UI 隐藏环）`() {
        assertNull(SubscriptionRules.renewProgressFraction(SubscriptionCycleType.MONTH, 1, null, 10))
        assertNull(SubscriptionRules.renewProgressFraction(SubscriptionCycleType.MONTH, 1, "2026/12/01", 10))
        assertNull(SubscriptionRules.renewProgressFraction(SubscriptionCycleType.MONTH, 1, "2026-12-01", null))
    }

    // ==================== isTerminal ====================

    @Test
    fun `终态判断 canceled 与 finished 为只读`() {
        assertTrue(SubscriptionRules.isTerminal("canceled"))
        assertTrue(SubscriptionRules.isTerminal("finished"))
        assertFalse(SubscriptionRules.isTerminal("active"))
        assertFalse(SubscriptionRules.isTerminal("paused"))
        assertFalse(SubscriptionRules.isTerminal(null))
        assertFalse(SubscriptionRules.isTerminal("garbage")) // 未知值防御回落 active
    }
}
