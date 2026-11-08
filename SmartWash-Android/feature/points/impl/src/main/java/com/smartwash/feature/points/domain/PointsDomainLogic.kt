package com.smartwash.feature.points.domain

import com.smartwash.feature.points.api.model.PointsOverviewVo
import com.smartwash.feature.points.api.model.SignInResultVo

/**
 * 积分域纯逻辑（连签周期进度 / 签到后 nextBonusIn 重算 / 榜单名次档位 / 月份与时间
 * 解析格式化 / 签到结果并入概览）——全部 internal 纯 JVM 函数，供锚点单测与页面消费，
 * 禁止在此触碰 Android 框架类型。
 */

/** 连签奖励周期（与后端 points.sign-in.streak-bonus-step 默认值对齐；端上仅用于进度展示与签后本地重算，奖励发放以下成为准） */
internal const val STREAK_BONUS_STEP = 7

/** 连签周期进度：currentInCycle = 周期内已签天数（0..stepDays-1），进度条渲染用 */
internal data class StreakCycleProgress(val currentInCycle: Int, val stepDays: Int)

/**
 * 由 nextBonusIn 反推周期内进度：距奖励还差 n 天 → 周期内已进 step-n 天。
 * 容错：null/<=0（字段缺失或已达成）与 >stepDays（契约漂移）一律回周期起点 0/step，
 * 进度条不越界不出负。
 */
internal fun streakCycleProgress(nextBonusIn: Int?, stepDays: Int = STREAK_BONUS_STEP): StreakCycleProgress {
    val remaining = nextBonusIn ?: 0
    if (remaining <= 0 || remaining > stepDays) return StreakCycleProgress(0, stepDays)
    return StreakCycleProgress(stepDays - remaining, stepDays)
}

/**
 * 签到成功后的 nextBonusIn 本地重算（签后立即更新余额/连签用，静默刷新以后端为准）：
 * 连签第 N×step 天发放奖励，故 streak 落在周期边界（余 0）时下一轮为整周期 stepDays，
 * 否则为 stepDays - 余数。
 */
internal fun nextBonusAfterSignIn(streak: Int, stepDays: Int = STREAK_BONUS_STEP): Int {
    val inCycle = streak % stepDays
    return if (inCycle == 0) stepDays else stepDays - inCycle
}

/** 榜单名次档位：TOP3 品牌色强调，其余中性（0/null/负数等异常一律 NORMAL） */
internal enum class RankTier { TOP1, TOP2, TOP3, NORMAL }

internal fun rankTier(rank: Int?): RankTier = when (rank) {
    1 -> RankTier.TOP1
    2 -> RankTier.TOP2
    3 -> RankTier.TOP3
    else -> RankTier.NORMAL
}

/**
 * 榜单月份解析（后端 month 为 yyyyMM）：合法返回 (年, 月) 二元组供 strings.xml 格式化，
 * 非法（长度/非数字/月份越界）返回 null，UI 不展示月份角标。
 */
internal fun parseLeaderboardMonth(month: String?): Pair<Int, Int>? {
    val s = month?.takeIf { it.length == 6 && it.all(Char::isDigit) } ?: return null
    val year = s.substring(0, 4).toIntOrNull() ?: return null
    val monthOfYear = s.substring(4, 6).toIntOrNull() ?: return null
    if (monthOfYear !in 1..12) return null
    return year to monthOfYear
}

/**
 * 流水时间格式化（ISO yyyy-MM-dd'T'HH:mm:ss → MM-dd HH:mm）。
 * 解析失败（旧格式/字段漂移）返回 null，UI 隐藏时间而非展示垃圾串。
 */
internal fun formatLedgerTime(iso: String?): String? {
    val s = iso?.takeIf { it.isNotBlank() } ?: return null
    return try {
        java.time.LocalDateTime.parse(s)
            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }
}

/**
 * 签到结果并入概览的乐观局部更新（成功后余额/连签/已签立即生效，不等静默刷新）：
 * - signedToday 置 true（幂等命中同样成立——当日已签）；
 * - balance/balanceAfter、streak 以签到结果为准（缺失保留原值）；
 * - nextBonusIn 本地重算（见 [nextBonusAfterSignIn]）；
 * - totalEarned 乐观累加本次实得（totalEarned 只增不减，负向防御 coerceAtLeast(0)）。
 */
internal fun PointsOverviewVo.updatedWith(result: SignInResultVo): PointsOverviewVo {
    val newStreak = result.streak ?: safeStreak
    return copy(
        signedToday = true,
        balance = result.balanceAfter ?: balance,
        streak = newStreak,
        nextBonusIn = nextBonusAfterSignIn(newStreak),
        totalEarned = (totalEarned ?: 0) + result.safePointsAwarded.coerceAtLeast(0),
    )
}
