package com.smartwash.subscription.constant;

import lombok.Getter;

/**
 * 订阅提醒类型。
 * renew：续费提醒（next_renew_date 落入 remind_days_before 窗口时触发，含已过期未推进的补提醒）；
 * trial_end：试用截止提醒（仅 trial_end_date 当日触发，对齐 Wallos 取消日语义）。
 */
@Getter
public enum SubscriptionRemindType {
    RENEW("renew", "续费提醒"),
    TRIAL_END("trial_end", "试用截止提醒");

    private final String type;
    private final String description;

    SubscriptionRemindType(String type, String description) {
        this.type = type;
        this.description = description;
    }
}
