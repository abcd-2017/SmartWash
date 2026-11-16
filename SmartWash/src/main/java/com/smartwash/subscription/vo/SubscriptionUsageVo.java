package com.smartwash.subscription.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class SubscriptionUsageVo {

    private Long usageId;

    private Long subscriptionId;

    private LocalDate usageDate;

    private String note;

    private LocalDateTime createdAt;
}
