package com.smartwash.feature.points.domain

import com.smartwash.feature.points.api.model.PointsOverviewVo
import com.smartwash.feature.points.api.model.SignInResultVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 积分域纯逻辑 JVM 单测（连签周期进度 / 签后 nextBonusIn 重算 / 榜单档位 /
 * 月份与流水时间解析 / 签到结果并入概览）。
 *
 * 锚点契约：STREAK_BONUS_STEP = 7 与后端 points.sign-in.streak-bonus-step 默认值对齐；
 * 连签第 N×step 天发放奖励，nextBonusIn = step - (streak % step)（余 0 时为整周期）。
 */
class PointsDomainLogicTest {

    // ==================== streakCycleProgress：nextBonusIn 反推周期内进度 ====================

    @Test
    fun `周期内进度 由 nextBonusIn 反推且不出负不越界`() {
        assertEquals(StreakCycleProgress(6, 7), streakCycleProgress(1)) // 距奖励 1 天 → 已进 6 天
        assertEquals(StreakCycleProgress(3, 7), streakCycleProgress(4)) // 距奖励 4 天 → 已进 3 天
        assertEquals(StreakCycleProgress(0, 7), streakCycleProgress(7)) // 差整周期 → 周期起点
    }

    @Test
    fun `周期内进度 异常输入一律回周期起点`() {
        // null / <=0（字段缺失或已达成）与 >stepDays（契约漂移）都回 0/step
        assertEquals(StreakCycleProgress(0, 7), streakCycleProgress(null))
        assertEquals(StreakCycleProgress(0, 7), streakCycleProgress(0))
        assertEquals(StreakCycleProgress(0, 7), streakCycleProgress(-1))
        assertEquals(StreakCycleProgress(0, 7), streakCycleProgress(8))
        // 自定义周期长度下同样成立
        assertEquals(StreakCycleProgress(0, 5), streakCycleProgress(6, stepDays = 5))
        assertEquals(StreakCycleProgress(4, 5), streakCycleProgress(1, stepDays = 5))
    }

    // ==================== nextBonusAfterSignIn：签后本地重算 ====================

    @Test
    fun `签后 nextBonusIn 周期边界回整周期`() {
        assertEquals(7, nextBonusAfterSignIn(streak = 0))  // 边界：周期起点
        assertEquals(7, nextBonusAfterSignIn(streak = 7))  // 第 7 天刚拿过奖 → 下轮整周期
        assertEquals(7, nextBonusAfterSignIn(streak = 14))
    }

    @Test
    fun `签后 nextBonusIn 周期内递减`() {
        assertEquals(6, nextBonusAfterSignIn(streak = 1))
        assertEquals(1, nextBonusAfterSignIn(streak = 6))  // 差 1 天拿奖
        assertEquals(6, nextBonusAfterSignIn(streak = 8))  // 第二轮周期第 1 天
        assertEquals(1, nextBonusAfterSignIn(streak = 13))
    }

    // ==================== rankTier：榜单名次档位 ====================

    @Test
    fun `榜单名次 1到3 落对应档位`() {
        assertEquals(RankTier.TOP1, rankTier(1))
        assertEquals(RankTier.TOP2, rankTier(2))
        assertEquals(RankTier.TOP3, rankTier(3))
    }

    @Test
    fun `榜单名次 异常值一律中性档`() {
        assertEquals(RankTier.NORMAL, rankTier(0))
        assertEquals(RankTier.NORMAL, rankTier(4))
        assertEquals(RankTier.NORMAL, rankTier(-1))
        assertEquals(RankTier.NORMAL, rankTier(null))
    }

    // ==================== parseLeaderboardMonth：yyyyMM 解析 ====================

    @Test
    fun `榜单月份 合法 yyyyMM 解析为年月`() {
        assertEquals(2026 to 11, parseLeaderboardMonth("202611"))
        assertEquals(2024 to 1, parseLeaderboardMonth("202401"))
        assertEquals(1999 to 12, parseLeaderboardMonth("199912"))
    }

    @Test
    fun `榜单月份 非法输入返回 null`() {
        assertNull(parseLeaderboardMonth(null))
        assertNull(parseLeaderboardMonth(""))
        assertNull(parseLeaderboardMonth("20261"))     // 长度不足
        assertNull(parseLeaderboardMonth("2026111"))   // 长度超出
        assertNull(parseLeaderboardMonth("2026ab"))    // 非数字
        assertNull(parseLeaderboardMonth("202613"))    // 月份越界
        assertNull(parseLeaderboardMonth("202600"))    // 月份为 0
    }

    // ==================== formatLedgerTime：ISO → MM-dd HH:mm ====================

    @Test
    fun `流水时间 ISO 格式化为 月日 时分`() {
        assertEquals("11-21 08:05", formatLedgerTime("2026-11-21T08:05:03"))
        assertEquals("01-02 23:59", formatLedgerTime("2024-01-02T23:59:59"))
    }

    @Test
    fun `流水时间 解析失败或缺失返回 null`() {
        assertNull(formatLedgerTime(null))
        assertNull(formatLedgerTime("   "))
        assertNull(formatLedgerTime("2026-11-21 08:05:03")) // 空格分隔（points 未做历史格式兼容，直接 null 隐藏时间）
        assertNull(formatLedgerTime("garbage"))
    }

    // ==================== updatedWith：签到结果并入概览（乐观局部更新） ====================

    private fun overview(
        balance: Int? = 100,
        totalEarned: Int? = 500,
        signedToday: Boolean? = false,
        streak: Int? = 6,
        nextBonusIn: Int? = 1,
    ) = PointsOverviewVo(
        balance = balance,
        totalEarned = totalEarned,
        signedToday = signedToday,
        streak = streak,
        nextBonusIn = nextBonusIn,
        recentLedger = emptyList(),
    )

    @Test
    fun `签到成功 并入后余额连签与周期重算生效`() {
        val merged = overview().updatedWith(
            SignInResultVo(signed = true, streak = 7, pointsAwarded = 5, balanceAfter = 105),
        )
        assertEquals(true, merged.signedToday)
        assertEquals(105, merged.balance)
        assertEquals(7, merged.streak)
        // 第 7 天刚拿奖 → 下轮整周期 7 天
        assertEquals(7, merged.nextBonusIn)
        assertEquals(505, merged.totalEarned) // 乐观累加本次实得
    }

    @Test
    fun `幂等命中同样置 signedToday 且 totalEarned 不变`() {
        val merged = overview().updatedWith(
            SignInResultVo(signed = false, alreadySigned = true, streak = 6, pointsAwarded = 0, balanceAfter = 100),
        )
        assertEquals(true, merged.signedToday)
        assertEquals(100, merged.balance)
        assertEquals(6, merged.streak)
        assertEquals(1, merged.nextBonusIn) // 周期内第 6 天 → 差 1 天拿奖
        assertEquals(500, merged.totalEarned)
    }

    @Test
    fun `签到结果字段缺失时保留概览原值`() {
        val merged = overview(balance = 50, totalEarned = 10, streak = 3).updatedWith(
            SignInResultVo(), // 全缺省：streak/balanceAfter 回落原值
        )
        assertEquals(50, merged.balance)
        assertEquals(3, merged.streak)
        assertEquals(4, merged.nextBonusIn) // 按 streak=3 重算
        assertEquals(10, merged.totalEarned)
    }

    @Test
    fun `负向积分不下调 totalEarned`() {
        val merged = overview(totalEarned = 100).updatedWith(
            SignInResultVo(pointsAwarded = -3, streak = 7, balanceAfter = 105),
        )
        assertEquals(100, merged.totalEarned) // coerceAtLeast(0) 防御
    }

    @Test
    fun `概览 totalEarned 缺失时从 0 起累加`() {
        val merged = overview(totalEarned = null).updatedWith(
            SignInResultVo(pointsAwarded = 8, streak = 1, balanceAfter = 8),
        )
        assertEquals(8, merged.totalEarned)
    }
}
