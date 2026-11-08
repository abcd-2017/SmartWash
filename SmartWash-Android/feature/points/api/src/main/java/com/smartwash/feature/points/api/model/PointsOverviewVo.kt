package com.smartwash.feature.points.api.model

import androidx.annotation.Keep

/**
 * 我的积分概览（GET /web/auth/points/me 响应载荷，与后端 PointsOverviewVo 一一对应）。
 *
 * Gson 容错约定（api 模块统一）：字段全部可空 + 默认 null——Gson 反序列化缺字段或
 * 类型漂移时置 null 而非抛出，消费方一律经 safe 系（safeXxx / isXxx）计算属性取兜底值，
 * 禁止直接读可空原始字段（避免隐式 NPE）。
 */
@Keep
data class PointsOverviewVo(
    /** 当前积分余额（无账户=0） */
    val balance: Int? = null,
    /** 累计获得积分（无账户=0，只增不减） */
    val totalEarned: Int? = null,
    /** 今日是否已签 */
    val signedToday: Boolean? = null,
    /**
     * 当前连签天数：今日已签 = 签到时点记录的 streak；今日未签 = 连签预览
     * （昨日有签到则昨日 streak+1，否则 0），提示「今天签将达 N 天」。
     */
    val streak: Int? = null,
    /** 距下次连签奖励还需的签到次数（含今日，若今日未签；周期见后端 points.sign-in.streak-bonus-step） */
    val nextBonusIn: Int? = null,
    /** 最近流水（时间倒序，条数见后端 points.leaderboard.recent-ledger-size） */
    val recentLedger: List<PointLedgerItemVo>? = null,
) {
    val safeBalance: Int get() = balance ?: 0
    val safeTotalEarned: Int get() = totalEarned ?: 0

    /** 今日是否已签（字段缺失按「未签」处理，宁可靠签到接口幂等兜底也不误置灰） */
    val isSignedToday: Boolean get() = signedToday == true
    val safeStreak: Int get() = streak ?: 0
    val safeNextBonusIn: Int get() = nextBonusIn ?: 0
    val safeRecentLedger: List<PointLedgerItemVo> get() = recentLedger.orEmpty()
}

/**
 * 积分流水条目（/me 最近流水列表元素，与后端 PointLedgerItemVo 一一对应）。
 * 日期/时间字段保留后端下发的 ISO 字符串原样（Gson 默认不支持 java.time 类型，
 * 解析与格式化由消费方容错处理，见 impl 的 formatLedgerTime）。
 */
@Keep
data class PointLedgerItemVo(
    /** 积分变动（正=入账，负=出账） */
    val delta: Int? = null,
    /** 业务原因枚举串（SIGN_IN / STREAK_BONUS，见后端 PointReason） */
    val reason: String? = null,
    /** 业务原因中文描述（后端直接展示用；端上映射缺失时的回退文案） */
    val reasonDesc: String? = null,
    /** 业务日期维度（签到类=签到日，ISO yyyy-MM-dd；v1 端上不展示，保留契约） */
    val refDate: String? = null,
    /** 本笔变动后余额快照 */
    val balanceAfter: Int? = null,
    /** 产生时间（ISO yyyy-MM-dd'T'HH:mm:ss） */
    val createdAt: String? = null,
) {
    val safeDelta: Int get() = delta ?: 0
    val safeBalanceAfter: Int get() = balanceAfter ?: 0
}
