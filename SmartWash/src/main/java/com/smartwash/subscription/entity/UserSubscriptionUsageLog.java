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
 * 订阅使用频率打卡流水（决策依据：近 3 月使用次数）。
 * 每订阅每天一条，可撤销后重打；仅作频率统计，不参与任何资金计算。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_subscription_usage_log")
public class UserSubscriptionUsageLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "usage_id", type = IdType.AUTO)
    private Long usageId;

    /** 订阅 ID */
    private Long subscriptionId;

    /** users.id（冗余，按用户隔离统计） */
    private Long userId;

    /** 使用日期 */
    private LocalDate usageDate;

    /** 使用备注 */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
