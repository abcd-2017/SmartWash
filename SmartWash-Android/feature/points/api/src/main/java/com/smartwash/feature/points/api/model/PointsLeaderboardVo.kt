package com.smartwash.feature.points.api.model

import androidx.annotation.Keep

/**
 * 积分月榜（GET /web/auth/points/leaderboard?scope=month 响应载荷，
 * 与后端 LeaderboardVo 一一对应）。数据源为 Redis ZSet 月榜（以 point_ledger 为
 * 事实源），榜与库允许短暂不一致。
 */
@Keep
data class PointsLeaderboardVo(
    /** 榜单口径（当前仅 month） */
    val scope: String? = null,
    /** 榜单月份（yyyyMM） */
    val month: String? = null,
    /** TOP N（N 见后端 points.leaderboard.size） */
    val entries: List<LeaderboardEntryVo>? = null,
    /** 我的排名（未上榜=null） */
    val my: MyRankVo? = null,
) {
    val safeEntries: List<LeaderboardEntryVo> get() = entries.orEmpty()
}

/** 月榜条目（后端 LeaderboardVo.EntryVo）：手机号已由后端脱敏，端上原样展示 */
@Keep
data class LeaderboardEntryVo(
    /** 名次（1 起） */
    val rank: Int? = null,
    /** 用户 ID（列表 item key） */
    val userId: Long? = null,
    /** 脱敏手机号（138****1234，后端脱敏后下发） */
    val maskedPhone: String? = null,
    /** 头像 URL（可空，空则端上占位） */
    val avatar: String? = null,
    /** 月内签到积分 */
    val points: Int? = null,
) {
    val safeRank: Int get() = rank ?: 0
    val safeMaskedPhone: String get() = maskedPhone.orEmpty()
    val safePoints: Int get() = points ?: 0
}

/** 我的月榜排名（后端 LeaderboardVo.MyRankVo，未上榜时 my 为 null） */
@Keep
data class MyRankVo(
    /** 名次（1 起，未上榜=null） */
    val rank: Int? = null,
    /** 月内签到积分 */
    val points: Int? = null,
) {
    val safeRank: Int get() = rank ?: 0
    val safePoints: Int get() = points ?: 0
}
