package com.smartwash.subscription.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅提醒视图对象（含订阅名称，App 内提醒列表消费）。
 */
@Data
@Accessors(chain = true)
public class SubscriptionReminderVo {

    private Long reminderId;

    private Long subscriptionId;

    /** 订阅名称（冗余展示，提醒列表免二次查询） */
    private String subscriptionName;

    /** renew续费/trial_end试用截止 */
    private String remindType;

    /** 应提醒日（=被提醒的目标日期） */
    private LocalDate remindDate;

    /** 已读 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}
