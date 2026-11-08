package com.smartwash.feature.points

import androidx.annotation.StringRes

/**
 * 积分流水业务原因枚举（与后端 PointReason 枚举名一一对应）。
 *
 * 文案映射在本模块 strings.xml（文案跟 UI 走，对齐 OrderStatus/ShowOrderStatus 的
 * descriptionRes 先例）；后端新增枚举值时 [from] 落 [UNKNOWN]，UI 回退展示后端下发
 * 的 reasonDesc（PointLedgerItemVo.reasonDesc），不崩不空白。
 */
enum class PointsReason(@StringRes val labelRes: Int?) {
    /** 每日签到基础分 */
    SIGN_IN(R.string.points_reason_sign_in),

    /** 连签奖励（每满周期天数额外发放一次） */
    STREAK_BONUS(R.string.points_reason_streak_bonus),

    /** 未收录原因（后端扩展枚举后端上未适配），回退 reasonDesc */
    UNKNOWN(null);

    companion object {
        /** reason 字符串 → 枚举（未收录/null 一律 UNKNOWN，不做异常分支） */
        fun from(code: String?): PointsReason = when (code) {
            "SIGN_IN" -> SIGN_IN
            "STREAK_BONUS" -> STREAK_BONUS
            else -> UNKNOWN
        }
    }
}
