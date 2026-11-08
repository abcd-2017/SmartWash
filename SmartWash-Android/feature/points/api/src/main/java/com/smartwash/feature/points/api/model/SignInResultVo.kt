package com.smartwash.feature.points.api.model

import androidx.annotation.Keep

/**
 * 签到结果（POST /web/auth/points/sign-in 响应载荷，与后端 SignInResultVo 一一对应）。
 * 后端幂等：重复请求返回本 VO 且 alreadySigned=true，code=200，不重复加分——
 * 端上按 alreadySigned 渲染「今日已签到」，不作为错误处理。
 */
@Keep
data class SignInResultVo(
    /** 本次是否真正签上（幂等命中=false） */
    val signed: Boolean? = null,
    /** 是否幂等命中（当日已签，含并发冲突方） */
    val alreadySigned: Boolean? = null,
    /** 签到时点的连签天数（幂等命中=既有记录的 streak） */
    val streak: Int? = null,
    /** 本次实得积分（幂等命中=0） */
    val pointsAwarded: Int? = null,
    /** 签到后余额（幂等命中=当前余额） */
    val balanceAfter: Int? = null,
) {
    val isAlreadySigned: Boolean get() = alreadySigned == true
    val safeStreak: Int get() = streak ?: 0
    val safePointsAwarded: Int get() = pointsAwarded ?: 0
    val safeBalanceAfter: Int get() = balanceAfter ?: 0
}
