package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderStatus

/**
 * 站内信 dining 深链加入入口决策（纯 Kotlin，JVM 单测锚点）。
 *
 * 深链 `smartwash://dining/order/{orderId}?t={token}` 落地后，端上先拉 brief：
 * - 已是成员 → 直达点单页（[ENTER_DETAIL]）；
 * - 非成员且订单进行中 → 展示标题预览 + 「加入」按钮（[CONFIRM_JOIN]）；
 * - 非成员且订单已结束/状态未知 → 不引导加入（[ORDER_FINISHED]）；
 * - brief 缺失/拉取失败 → 页面按错误态处理（[BRIEF_UNAVAILABLE]）。
 *
 * 状态保守降级：[DiningOrderBriefUi.status] 由映射层对未知状态降级为 FINISHED
 * （见 DiningMappers），故未知态一律按「不可加入」处理，避免对脏数据订单引导加入。
 */
enum class DiningJoinEntryDecision {
    /** 已是成员：直达点单详情页 */
    ENTER_DETAIL,

    /** 非成员且订单进行中：展示确认加入 */
    CONFIRM_JOIN,

    /** 订单已结束/状态未知：不引导加入 */
    ORDER_FINISHED,

    /** brief 缺失/拉取失败：页面按错误态处理 */
    BRIEF_UNAVAILABLE,
}

object DiningJoinEntry {

    fun decide(brief: DiningOrderBriefUi?): DiningJoinEntryDecision = when {
        brief == null -> DiningJoinEntryDecision.BRIEF_UNAVAILABLE
        brief.joined -> DiningJoinEntryDecision.ENTER_DETAIL
        brief.status == DiningOrderStatus.ONGOING -> DiningJoinEntryDecision.CONFIRM_JOIN
        else -> DiningJoinEntryDecision.ORDER_FINISHED
    }
}
