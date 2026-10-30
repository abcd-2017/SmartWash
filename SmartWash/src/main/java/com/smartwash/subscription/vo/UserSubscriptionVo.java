package com.smartwash.subscription.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅视图对象（用户端，含派生展示字段）。
 */
@Data
@Accessors(chain = true)
public class UserSubscriptionVo {

    private Long subscriptionId;

    private Long userId;

    /** 订阅名称 */
    private String name;

    /** 每期金额 */
    private BigDecimal amount;

    /** 计费周期 month/year */
    private String cycleType;

    /** 间隔期数（如每2月=2） */
    private Integer cycleInterval;

    /** 下次续费日 */
    private LocalDate nextRenewDate;

    /** 起始锚点日（保留字段） */
    private LocalDate firstAnchorDate;

    /** 试用截止日（NULL=非试用） */
    private LocalDate trialEndDate;

    /** 自动续费 0否 1是 */
    private Integer autoRenew;

    /** active生效中/paused已暂停/canceled已取消/finished已结束 */
    private String status;

    /** 取消入口网址 */
    private String cancelUrl;

    /** 取消路径步骤备忘 */
    private String cancelSteps;

    /** 续费/试用截止前提醒天数 */
    private Integer remindDaysBefore;

    /** 备注 */
    private String note;

    /** 距下次续费天数（负数=已过期待推进，展示层可标红） */
    private Long daysUntilRenew;

    /** 距试用截止天数（非试用为 null，负数=已过截止） */
    private Long trialDaysLeft;

    /** 未读提醒数（列表/详情红点） */
    private Integer unreadReminderCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
