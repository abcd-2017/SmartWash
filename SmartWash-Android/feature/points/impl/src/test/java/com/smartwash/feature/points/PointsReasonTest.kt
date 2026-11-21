package com.smartwash.feature.points

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 积分流水原因枚举映射 JVM 单测（与后端 PointReason 枚举名一一对应）。
 *
 * 契约锚点：后端新增枚举值时 [PointsReason.from] 落 UNKNOWN，UI 回退展示
 * 后端下发的 reasonDesc——映射不允许抛异常、不允许映射成已知枚举。
 */
class PointsReasonTest {

    @Test
    fun `已知 reason 字符串映射到对应枚举`() {
        assertEquals(PointsReason.SIGN_IN, PointsReason.from("SIGN_IN"))
        assertEquals(PointsReason.STREAK_BONUS, PointsReason.from("STREAK_BONUS"))
    }

    @Test
    fun `未收录与缺失 reason 一律落 UNKNOWN`() {
        assertEquals(PointsReason.UNKNOWN, PointsReason.from("DAILY_TASK"))
        assertEquals(PointsReason.UNKNOWN, PointsReason.from("redeem"))
        assertEquals(PointsReason.UNKNOWN, PointsReason.from(""))
        assertEquals(PointsReason.UNKNOWN, PointsReason.from(null))
    }

    @Test
    fun `大小写敏感 后端枚举名为大写`() {
        // 故意小写：映射必须按后端原值精确匹配，不静默做大小写归一
        assertEquals(PointsReason.UNKNOWN, PointsReason.from("sign_in"))
    }

    @Test
    fun `文案资源契约 已知项带 labelRes UNKNOWN 为空`() {
        assertEquals(R.string.points_reason_sign_in, PointsReason.SIGN_IN.labelRes)
        assertEquals(R.string.points_reason_streak_bonus, PointsReason.STREAK_BONUS.labelRes)
        assertNull(PointsReason.UNKNOWN.labelRes) // UNKNOWN 走 reasonDesc 回退，不占 res
    }

    @Test
    fun `枚举全集恰为三项`() {
        assertEquals(listOf("SIGN_IN", "STREAK_BONUS", "UNKNOWN"), PointsReason.entries.map { it.name })
    }
}
