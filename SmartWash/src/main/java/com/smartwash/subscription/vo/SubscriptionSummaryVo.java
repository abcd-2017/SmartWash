package com.smartwash.subscription.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 管理端订阅汇总视图对象（GET /admin/subscription/summary）。
 * 口径以 status + next_renew_date 判定：本域无独立 expired 状态，
 * 已过期 = 仍为 active 但下次续费日已过（逾期未推进，定时任务补提醒/推进窗口内）。
 */
@Data
@Accessors(chain = true)
public class SubscriptionSummaryVo {

    /** 生效中订阅数（status = active） */
    private Long activeCount;

    /** 已过期订阅数（status = active 且 next_renew_date < 今日，逾期未推进） */
    private Long expiredCount;

    /** 7 日内到期订阅数（status = active 且 next_renew_date ∈ [今日, 今日+7]） */
    private Long expiringIn7Days;
}
