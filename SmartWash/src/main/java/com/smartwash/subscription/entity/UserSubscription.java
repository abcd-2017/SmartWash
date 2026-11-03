package com.smartwash.subscription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户订阅主档。
 * 周期三元组（cycle_type/cycle_interval/first_anchor_date）描述计费周期，next_renew_date 为推进落点；
 * 续费日推进由定时任务按 CAS 条件更新完成，状态流转统一走 casTransition 条件闸门，禁止 check-then-act 裸更新。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_subscription")
public class UserSubscription implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "subscription_id", type = IdType.AUTO)
    private Long subscriptionId;

    /** users.id，数据完全以当前登录用户为主体隔离 */
    private Long userId;

    /** 订阅名称 */
    private String name;

    /** 每期金额 */
    private BigDecimal amount;

    /** 计费周期 month/year */
    private String cycleType;

    /** 间隔期数（如每2月=2） */
    private Integer cycleInterval;

    /** 下次续费日（定时任务推进的落点） */
    private LocalDate nextRenewDate;

    /** 起始锚点日（月末策略锚，保留字段，v1 推进固定用 LocalDate.plusMonths clamp） */
    private LocalDate firstAnchorDate;

    /** 试用截止日（NULL=非试用） */
    private LocalDate trialEndDate;

    /** 自动续费 0否 1是（仅控制定时推进，不影响提醒） */
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
