package com.smartwash.subscription.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅使用打卡记录视图对象。
 */
@Data
@Accessors(chain = true)
public class SubscriptionUsageVo {

    private Long usageId;

    private Long subscriptionId;

    /** 使用日期 */
    private LocalDate usageDate;

    /** 使用备注 */
    private String note;

    private LocalDateTime createdAt;
}
