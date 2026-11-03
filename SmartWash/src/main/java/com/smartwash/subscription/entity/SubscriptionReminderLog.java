package com.smartwash.subscription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅提醒记录。
 * remind_date 为被提醒的目标日期（非扫描日），配合 uk_sub_type_date 唯一索引实现
 * 「同一订阅同类型同日只发一次」的幂等（renewlet notification_jobs 先例），重复插入由定时任务吞 DuplicateKey 兜底。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("subscription_reminder_log")
public class SubscriptionReminderLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "reminder_id", type = IdType.AUTO)
    private Long reminderId;

    /** 订阅 ID */
    private Long subscriptionId;

    /** users.id */
    private Long userId;

    /** renew续费/trial_end试用截止 */
    private String remindType;

    /** 应提醒日（=被提醒的目标日期，幂等键组成部分） */
    private LocalDate remindDate;

    /** 已读 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
